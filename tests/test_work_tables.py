from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from qu_imager.persistence.base import Base
from qu_imager.persistence.models import IndicationRow, JobRow, RadiographRow, WeldRow
from qu_imager.quantities.length import Length, mm

OPENED = date(2024, 7, 1)
SHOT = datetime(2024, 7, 10, 23, 15)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def job_row(year=24, serial=417):
    return JobRow(
        year=year,
        serial=serial,
        client="Maritime Pipelines",
        site="Come By Chance",
        acceptance_code="API 1104",
        procedure_number="RT-001",
        opened_on=OPENED,
    )


def weld_row(number=104, repair=0, wall=None, length=None):
    return WeldRow(
        prefix="GW",
        number=number,
        repair_round=repair,
        material_key="carbon_steel",
        outside_diameter=mm("168.3"),
        wall=wall if wall is not None else mm("7.11"),
        reinforcement=mm(2),
        weld_length=length if length is not None else mm(529),
        welder="w-19",
        welded_on=OPENED,
    )


def radiograph_row(number="R-001", index=1):
    return RadiographRow(
        number=number,
        exposure_index=index,
        exposed_at=SHOT,
        technique_reference="RT-001 rev 1",
        medium="film",
        admissible=True,
    )


def test_a_job_holds_welds_and_a_weld_holds_radiographs():
    with session() as db:
        job = job_row()
        weld = weld_row()
        film = radiograph_row()
        weld.radiographs.append(film)
        job.welds.append(weld)
        db.add(job)
        db.commit()
        found = db.scalars(select(RadiographRow)).one()
        assert found.weld.number == 104
        assert found.weld.job.client == "Maritime Pipelines"


def test_two_jobs_cannot_share_a_year_and_serial():
    with session() as db:
        db.add(job_row())
        db.add(job_row())
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_job_serial_outside_the_range_is_refused():
    with session() as db:
        db.add(job_row(serial=0))
        with pytest.raises(IntegrityError):
            db.commit()
    with session() as db:
        db.add(job_row(year=100))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_repair_is_a_round_of_the_same_weld_and_both_rows_are_allowed():
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(repair=0))
        job.welds.append(weld_row(repair=1))
        db.add(job)
        db.commit()
        assert len(db.scalars(select(WeldRow)).all()) == 2


def test_the_same_weld_and_round_twice_is_refused():
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(repair=1))
        job.welds.append(weld_row(repair=1))
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()


def test_the_same_weld_number_on_two_jobs_is_fine():
    with session() as db:
        first = job_row(serial=417)
        second = job_row(serial=418)
        first.welds.append(weld_row())
        second.welds.append(weld_row())
        db.add_all([first, second])
        db.commit()
        assert len(db.scalars(select(WeldRow)).all()) == 2


def test_a_weld_with_no_wall_or_no_length_is_refused():
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(wall=Length.ZERO))
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()
    with session() as db:
        job = job_row()
        job.welds.append(weld_row(length=Length.ZERO))
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_radiograph_number_is_unique_across_the_whole_shop():
    with session() as db:
        job = job_row()
        first = weld_row(number=104)
        second = weld_row(number=105)
        first.radiographs.append(radiograph_row("R-001", 1))
        second.radiographs.append(radiograph_row("R-001", 1))
        job.welds.extend([first, second])
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()


def test_the_same_exposure_index_twice_on_one_weld_is_refused():
    with session() as db:
        job = job_row()
        weld = weld_row()
        weld.radiographs.append(radiograph_row("R-001", 1))
        weld.radiographs.append(radiograph_row("R-002", 1))
        job.welds.append(weld)
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()


def test_exposures_are_numbered_from_one_in_the_schema_too():
    with session() as db:
        job = job_row()
        weld = weld_row()
        weld.radiographs.append(radiograph_row("R-001", 0))
        job.welds.append(weld)
        db.add(job)
        with pytest.raises(IntegrityError):
            db.commit()


def test_indications_hang_off_the_film_they_were_seen_on():
    with session() as db:
        job = job_row()
        weld = weld_row()
        film = radiograph_row()
        film.indications.append(
            IndicationRow(
                kind="ESI",
                starts_at=mm(100),
                length=mm(20),
                width=mm(1),
                clock_minutes=180,
            )
        )
        weld.radiographs.append(film)
        job.welds.append(weld)
        db.add(job)
        db.commit()
        found = db.scalars(select(IndicationRow)).one()
        assert found.radiograph.number == "R-001"
        assert found.starts_at == mm(100)


def test_deleting_a_job_takes_the_whole_tree_with_it():
    with session() as db:
        job = job_row()
        weld = weld_row()
        film = radiograph_row()
        film.indications.append(
            IndicationRow(kind="P", starts_at=mm(100), length=Length.ZERO, width=mm(2))
        )
        weld.radiographs.append(film)
        job.welds.append(weld)
        db.add(job)
        db.commit()
        db.delete(job)
        db.commit()
        assert db.scalars(select(WeldRow)).all() == []
        assert db.scalars(select(RadiographRow)).all() == []
        assert db.scalars(select(IndicationRow)).all() == []


def test_a_reading_keeps_its_numbers_as_decimals():
    with session() as db:
        job = job_row()
        weld = weld_row()
        film = radiograph_row()
        film.density_through_indicator = Decimal("2.55")
        film.normalised_snr = Decimal("132.4")
        weld.radiographs.append(film)
        job.welds.append(weld)
        db.add(job)
        db.commit()
        found = db.scalars(select(RadiographRow)).one()
        assert found.density_through_indicator == Decimal("2.55")
        assert found.normalised_snr == Decimal("132.4")
