from datetime import date, datetime

from qu_imager.persistence.models.counters import CounterRow
from qu_imager.persistence.models.nonconformance import NonconformanceRow
from qu_imager.persistence.models.sources import DeviceRow, SourceRow, TripRow
from qu_imager.persistence.models.store import StoredImageRow
from qu_imager.persistence.unit_of_work import UnitOfWork, transaction
from qu_imager.safety.maintenance import quarterly
from qu_imager.services.doctor import (
    check_names,
    double_bookings,
    examine,
    numbering_gaps,
    orphaned_images,
    self_closed_reports,
    undecided_closures,
    unknown_devices,
)
from qu_imager.services.reading import ReadingRecord, record_reading
from test_reading_service import prepared

from conftest import a_reader, database

READ = datetime(2024, 9, 25, 8, 0)
RAISED = date(2024, 9, 26)


def a_source_row(serial="IR-5512"):
    from qu_imager.quantities.activity import curie
    from qu_imager.quantities.length import mm

    return SourceRow(
        serial=serial,
        isotope_key="ir192",
        assay_activity=curie(90),
        assay_at=datetime(2024, 8, 1),
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
        working_floor=curie(15),
        state="in service",
    )


def legacy(work, row):
    """Insert a row the current constraints would refuse.

    Every check in the doctor is for something the code no longer lets in,
    so a test that goes through the ordinary path cannot produce one. Turning
    the check constraints off for one insert is how a row from before the
    constraint existed gets simulated, and it is the only honest way to test
    a check whose whole purpose is legacy data.
    """
    connection = work.session.connection()
    connection.exec_driver_sql("PRAGMA ignore_check_constraints = 1")
    try:
        work.session.add(row)
        work.flush()
    finally:
        connection.exec_driver_sql("PRAGMA ignore_check_constraints = 0")


def a_row(reference="NCR-1", raised_by="0142", closed_by=None, disposition=None, closed_on=None):
    return NonconformanceRow(
        reference=reference,
        job="24-0001",
        item="GW-104",
        requirement="API 1104",
        account="slag",
        raised_on=RAISED,
        raised_by=raised_by,
        disposition=disposition,
        closed_by=closed_by,
        closed_on=closed_on,
    )


def test_a_healthy_shop_finds_nothing():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        record_reading(work, ReadingRecord(numbers[0], a_reader(), READ))
        found = examine(work)
    assert found.is_healthy
    assert "nothing found" in found.describe()
    assert len(found.checks_run) == len(check_names())


def test_an_empty_database_is_healthy():
    with transaction(database()) as work:
        assert examine(work).is_healthy


def test_a_source_out_on_two_trips_is_found():
    with UnitOfWork(database()) as work:
        source = a_source_row()
        device = DeviceRow(serial="D-77", model_key="880d")
        work.session.add_all([source, device])
        work.flush()
        for site in ("one", "two"):
            work.session.add(
                TripRow(
                    source_id=source.id,
                    device_id=device.id,
                    crew_badge="0142",
                    site=site,
                    went_out=RAISED,
                )
            )
        work.flush()
        found = double_bookings(work)
    assert len(found) == 1
    assert found[0].subject == "IR-5512"
    assert "2 open trips" in found[0].account


def test_a_source_out_once_is_not_a_double_booking():
    with UnitOfWork(database()) as work:
        source = a_source_row()
        device = DeviceRow(serial="D-77", model_key="880d")
        work.session.add_all([source, device])
        work.flush()
        work.session.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="one",
                went_out=RAISED,
            )
        )
        work.flush()
        assert double_bookings(work) == ()


def test_films_issued_through_the_counter_have_no_numbering_finding():
    with transaction(database()) as work:
        prepared(work, shots=3)
        assert numbering_gaps(work) == ()


def test_a_film_numbered_past_the_counter_is_found():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        row = work.session.scalars(
            __import__("sqlalchemy").select(CounterRow).where(CounterRow.name == "radiograph")
        ).one()
        row.next_value = 1
        work.flush()
        found = numbering_gaps(work)
    assert len(found) == 1
    assert "past a counter" in found[0].account


def test_films_with_no_counter_at_all_are_found():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        for row in work.session.scalars(__import__("sqlalchemy").select(CounterRow)).all():
            work.session.delete(row)
        work.flush()
        found = numbering_gaps(work)
    assert "no counter" in found[0].account


def test_a_file_whose_exposure_is_gone_is_found():
    with UnitOfWork(database()) as work:
        work.session.add(
            StoredImageRow(
                radiograph_id=999,
                filename="24-0001-R0001.dcm",
                image_format="dcm",
                purpose="record",
                revision=0,
                bit_depth=16,
                width=2048,
                height=512,
                byte_size=512,
                digest="a" * 64,
                captured_at=READ,
                ingested_at=READ,
                ingested_by="0142",
            )
        )
        work.flush()
        found = orphaned_images(work)
    assert found[0].subject == "24-0001-R0001.dcm"


def test_an_inspection_against_a_device_nobody_registered_is_found():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-99", RAISED, "0303"))
        work.flush()
        found = unknown_devices(work)
    assert found[0].subject == "D-99"


def test_a_report_closed_by_its_author_is_found():
    with UnitOfWork(database()) as work:
        legacy(work, a_row(closed_by="0142", disposition="rework", closed_on=date(2024, 10, 1)))
        found = self_closed_reports(work)
    assert found[0].subject == "NCR-1"
    assert "0142" in found[0].account


def test_a_report_closed_by_somebody_else_is_not():
    with UnitOfWork(database()) as work:
        legacy(work, a_row(closed_by="0303", disposition="rework", closed_on=date(2024, 10, 1)))
        assert self_closed_reports(work) == ()


def test_a_report_closed_with_nothing_decided_is_found():
    with UnitOfWork(database()) as work:
        legacy(work, a_row(closed_by="0303", closed_on=date(2024, 10, 1)))
        found = undecided_closures(work)
    assert found[0].subject == "NCR-1"


def test_findings_are_grouped_by_the_check_that_found_them():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-99", RAISED, "0303"))
        legacy(work, a_row(closed_by="0303", closed_on=date(2024, 10, 1)))
        grouped = examine(work).by_check()
    assert set(grouped) == {"unknown device", "undecided closure"}


def test_only_the_named_checks_run_when_they_are_asked_for():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-99", RAISED, "0303"))
        work.flush()
        found = examine(work, only=("self closed",))
    assert found.checks_run == ("self closed",)
    assert found.is_healthy


def test_a_finding_prints_the_check_the_subject_and_the_account():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-99", RAISED, "0303"))
        work.flush()
        line = examine(work).findings[0].describe()
    assert line.startswith("unknown device: D-99 ")
