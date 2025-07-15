from datetime import date, datetime, timedelta
from decimal import Decimal

from qu_imager.nonconformance import Report
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.safety.maintenance import quarterly
from qu_imager.services.morning import films_waiting, loads_ready, sheet_for
from qu_imager.services.sources import register_device, register_source
from qu_imager.services.wipes import record_wipe
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import SealedSource, SourceCertificate

from conftest import database, pass_inspections

ASSAY = datetime(2025, 6, 1)
DAY = date(2025, 7, 10)


def a_source(serial="IR-5512"):
    return SealedSource(
        certificate=SourceCertificate(
            serial=serial,
            isotope=IR192,
            assay_activity=curie(90),
            assay_at=ASSAY,
            manufacturer="QSA",
            model="A424-9",
            active_diameter=mm(3),
            active_length=mm(3),
        ),
        working_floor=curie(15),
    )


def a_shop(work, wiped_at=ASSAY, inspected=True):
    register_source(work, a_source())
    register_device(work, "D-77", "880d")
    if wiped_at is not None:
        record_wipe(work, "IR-5512", wiped_at, Decimal(0), "rp")
    if inspected:
        pass_inspections(work, "D-77", DAY)


def a_report(reference="NCR-1", raised_on=DAY):
    return Report(
        reference=reference,
        job="24-0001",
        item="GW-104",
        requirement="API 1104 9.3.8",
        account="elongated slag",
        raised_on=raised_on,
        raised_by="0142",
    )


def test_a_shop_with_everything_current_is_quiet():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 7, 1))
        sheet = sheet_for(work, DAY)
    assert sheet.is_quiet
    assert "nothing outstanding" in sheet.describe()


def test_a_device_nobody_inspected_stops_work_today():
    with transaction(database()) as work:
        a_shop(work, inspected=False)
        sheet = sheet_for(work, DAY)
    assert any("no quarterly" in line.account for line in sheet.stops_today)


def test_a_quarterly_that_ran_out_stops_work_today():
    with transaction(database()) as work:
        a_shop(work, inspected=False)
        work.maintenance.record(quarterly("D-77", date(2025, 1, 1), "0303"))
        sheet = sheet_for(work, DAY)
    assert any("ran out" in line.account for line in sheet.stops_today)


def test_a_quarterly_due_next_week_is_this_week_and_not_today():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 7, 1), inspected=False)
        work.maintenance.record(quarterly("D-77", DAY - timedelta(days=88), "0303"))
        sheet = sheet_for(work, DAY)
    assert sheet.stops_today == ()
    assert any("quarterly due by" in line.account for line in sheet.stops_this_week)


def test_a_source_with_no_wipe_test_stops_work_today():
    with transaction(database()) as work:
        a_shop(work, wiped_at=None)
        sheet = sheet_for(work, DAY)
    assert any("no wipe test" in line.account for line in sheet.stops_today)


def test_a_wipe_test_that_ran_out_stops_work_today():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2024, 6, 1))
        sheet = sheet_for(work, DAY)
    assert any("wipe test ran out" in line.account for line in sheet.stops_today)


def test_a_wipe_test_due_next_week_is_this_week():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 1, 15))
        sheet = sheet_for(work, DAY)
    assert any("wipe test due by" in line.account for line in sheet.stops_this_week)


def test_an_undecided_report_is_this_week():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 7, 1))
        work.nonconformances.raise_report(a_report())
        sheet = sheet_for(work, DAY)
    assert any("undecided" in line.account for line in sheet.stops_this_week)


def test_a_report_open_two_months_is_running_late():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 7, 1))
        work.nonconformances.raise_report(a_report(raised_on=DAY - timedelta(days=60)))
        sheet = sheet_for(work, DAY)
    assert any("open 60 days" in line.account for line in sheet.running_late)
    assert sheet.stops_this_week == ()


def test_the_sheet_reads_today_then_this_week_then_late():
    with transaction(database()) as work:
        a_shop(work, wiped_at=None, inspected=False)
        work.nonconformances.raise_report(a_report(raised_on=DAY - timedelta(days=60)))
        sheet = sheet_for(work, DAY)
    urgencies = [line.urgency for line in sheet.lines]
    assert urgencies[0] == "today"
    assert urgencies[-1] == "late"
    assert "outstanding on" in sheet.describe()


def test_a_line_prints_the_subject_and_the_account():
    with transaction(database()) as work:
        a_shop(work, inspected=False)
        line = sheet_for(work, DAY).stops_today[0]
    assert line.describe().startswith("D-77: ")


def test_the_pairings_that_could_leave_are_listed():
    with transaction(database()) as work:
        a_shop(work, wiped_at=datetime(2025, 7, 1))
        assert loads_ready(work, DAY) == (("D-77", "IR-5512"),)


def test_a_shop_that_cannot_send_anything_out_lists_nothing():
    with transaction(database()) as work:
        a_shop(work, inspected=False)
        assert loads_ready(work, DAY) == ()


def test_the_films_waiting_are_the_unread_ones():
    from test_reading_service import prepared

    with transaction(database()) as work:
        job, numbers = prepared(work, shots=2)
        waiting = films_waiting(work, date(2024, 9, 24))
    assert sorted(waiting) == sorted(numbers)


def test_a_shop_with_no_open_jobs_has_no_films_waiting():
    with transaction(database()) as work:
        a_shop(work)
        assert films_waiting(work, DAY) == ()
