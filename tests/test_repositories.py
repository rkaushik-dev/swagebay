from datetime import date, datetime

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from qu_imager.errors import NotFoundError
from qu_imager.identity import JobNumber, WeldId
from qu_imager.persistence.base import Base
from qu_imager.persistence.models import (
    DeviceRow,
    JobRow,
    RadiographRow,
    SourceRow,
    TripRow,
    WeldRow,
)
from qu_imager.persistence.repositories import (
    JobRepository,
    SourceRepository,
    WeldRepository,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import Length, mm

ASSAY = datetime(2024, 8, 1)
OPENED = date(2024, 8, 1)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def source_row(serial="IR-5512", state="in service"):
    return SourceRow(
        serial=serial,
        isotope_key="ir192",
        assay_activity=curie(90),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
        state=state,
        working_floor=curie(15),
    )


def job_row(serial=417, closed=None):
    return JobRow(
        year=24,
        serial=serial,
        client="c",
        site="s",
        acceptance_code="API 1104",
        procedure_number="RT-001",
        opened_on=OPENED,
        closed_on=closed,
    )


def weld_row(number=104, repair=0):
    return WeldRow(
        prefix="GW",
        number=number,
        repair_round=repair,
        material_key="carbon_steel",
        wall=mm(10),
        reinforcement=Length.ZERO,
        weld_length=mm(500),
        welder="w-19",
        welded_on=OPENED,
    )


def film(number="R-001", index=1, admissible=True, read=True):
    return RadiographRow(
        number=number,
        exposure_index=index,
        exposed_at=datetime(2024, 8, 20, 23, 0),
        technique_reference="RT-001 rev 1",
        medium="film",
        admissible=admissible,
        read_by="0142" if read else None,
        read_at=datetime(2024, 8, 21, 8, 0) if read else None,
    )


def test_a_source_is_found_by_serial():
    with session() as db:
        repository = SourceRepository(db)
        repository.add(source_row())
        db.commit()
        assert repository.get("IR-5512").isotope_key == "ir192"
        assert repository.find("IR-9999") is None


def test_a_missing_source_raises_with_the_identity_on_it():
    with session() as db, pytest.raises(NotFoundError) as caught:
        SourceRepository(db).get("IR-9999")
    assert caught.value.identity == "IR-9999"
    assert caught.value.kind == "source"


def test_only_the_sources_in_service_come_back():
    with session() as db:
        repository = SourceRepository(db)
        repository.add(source_row("IR-5512"))
        repository.add(source_row("IR-6000", state="awaiting disposal"))
        db.commit()
        assert [s.serial for s in repository.in_service()] == ["IR-5512"]


def test_the_open_trip_is_the_one_with_no_return():
    with session() as db:
        repository = SourceRepository(db)
        source = source_row()
        device = DeviceRow(serial="880D-221", model_key="880d")
        db.add_all([source, device])
        db.flush()
        db.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="a",
                went_out=date(2024, 8, 1),
                came_back=date(2024, 8, 2),
            )
        )
        db.commit()
        assert repository.open_trip("IR-5512") is None
        db.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="b",
                went_out=date(2024, 8, 5),
            )
        )
        db.commit()
        assert repository.open_trip("IR-5512").site == "b"


def test_a_missing_device_raises_too():
    with session() as db, pytest.raises(NotFoundError):
        SourceRepository(db).device("880D-999")


def test_a_job_is_found_by_its_number():
    with session() as db:
        repository = JobRepository(db)
        repository.add(job_row())
        db.commit()
        assert repository.get(JobNumber.parse("24-0417")).client == "c"
        assert repository.find(JobNumber.parse("24-0999")) is None


def test_a_missing_job_raises_with_the_number_on_it():
    with session() as db, pytest.raises(NotFoundError) as caught:
        JobRepository(db).get(JobNumber.parse("24-0999"))
    assert caught.value.identity == "24-0999"


def test_the_jobs_open_on_a_day_exclude_the_closed_ones():
    with session() as db:
        repository = JobRepository(db)
        repository.add(job_row(417))
        repository.add(job_row(418, closed=date(2024, 8, 10)))
        db.commit()
        assert [j.serial for j in repository.open_on(date(2024, 8, 20))] == [417]
        assert len(repository.open_on(date(2024, 8, 5))) == 2
        assert repository.open_on(date(2024, 7, 1)) == ()


def test_the_next_serial_follows_the_highest_used():
    with session() as db:
        repository = JobRepository(db)
        assert repository.next_serial(24) == 1
        repository.add(job_row(417))
        db.commit()
        assert repository.next_serial(24) == 418
        assert repository.next_serial(25) == 1


def test_a_weld_is_found_by_its_identifier_and_round():
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(104, 0))
        job.welds.append(weld_row(104, 1))
        db.add(job)
        db.commit()
        repository = WeldRepository(db)
        assert repository.get(job, WeldId.parse("GW-104")).repair_round == 0
        assert repository.get(job, WeldId.parse("GW-104R1")).repair_round == 1
        assert repository.find(job, WeldId.parse("GW-999")) is None


def test_every_round_of_a_weld_comes_back_original_first():
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(104, 1))
        job.welds.append(weld_row(104, 0))
        db.add(job)
        db.commit()
        rounds = WeldRepository(db).rounds(job, WeldId.parse("GW-104"))
        assert [r.repair_round for r in rounds] == [0, 1]


def test_the_unread_and_inadmissible_films_are_found_per_job():
    with session() as db:
        job = job_row()
        weld = weld_row()
        weld.radiographs.append(film("R-001", 1))
        weld.radiographs.append(film("R-002", 2, read=False))
        weld.radiographs.append(film("R-003", 3, admissible=False))
        job.welds.append(weld)
        db.add(job)
        db.commit()
        repository = WeldRepository(db)
        assert [r.number for r in repository.unread(job)] == ["R-002"]
        assert [r.number for r in repository.inadmissible(job)] == ["R-003"]


def test_a_radiograph_is_found_by_its_number_across_the_shop():
    with session() as db:
        job = job_row()
        weld = weld_row()
        weld.radiographs.append(film("R-001", 1))
        job.welds.append(weld)
        db.add(job)
        db.commit()
        repository = WeldRepository(db)
        assert repository.radiograph("R-001").exposure_index == 1
        with pytest.raises(NotFoundError):
            repository.radiograph("R-404")


def test_a_repository_never_commits_on_its_own():
    with session() as db:
        SourceRepository(db).add(source_row())
        db.rollback()
        assert SourceRepository(db).find("IR-5512") is None
