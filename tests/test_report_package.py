from datetime import date, datetime
from decimal import Decimal

import pytest

from qu_imager.errors import NotFoundError
from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.admissibility import Evidence, IndicatorEvidence
from qu_imager.imaging.density import Calibration, Reading, grade
from qu_imager.imaging.film import film
from qu_imager.imaging.indicators import HoleIndicator, HoleSize
from qu_imager.imaging.selection import TechniqueClass
from qu_imager.imaging.system import ImagingSystem
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.personnel import Level, LicenceRole, Person
from qu_imager.quantities.length import mm
from qu_imager.reporting.package import assemble
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.reading import ReadingRecord, record_reading
from qu_imager.services.shooting import ShotRecord, record_shot

OPENED = date(2024, 11, 1)
SHOT = datetime(2024, 11, 5, 23, 0)
READ = datetime(2024, 11, 6, 8, 0)
ON_FILM = ImagingSystem.from_film(film("d7"))


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def reader():
    return Person(
        badge="0142",
        name="reader",
        level=Level.LEVEL_II,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=date(2023, 1, 15),
        near_vision_on=date(2024, 6, 1),
        colour_perception_on=date(2024, 6, 1),
    )


def evidence(visible=True):
    return Evidence(
        system=ON_FILM,
        penetrated=mm(26),
        technique_class=TechniqueClass.A,
        indicator=IndicatorEvidence(
            required_hole=HoleIndicator(30),
            required_hole_size=HoleSize.TWO_T,
            hole_visible=visible,
        ),
        density=grade(
            (
                Reading("indicator", Decimal("2.50"), through_indicator=True),
                Reading("weld", Decimal("2.50")),
            ),
            gamma=True,
            calibration=Calibration(SHOT, Decimal("2.00"), Decimal("2.01"), "X-Rite"),
            read_at=SHOT,
        ),
    )


def a_job(work):
    return open_job(
        work,
        NewJob(
            client="Maritime Pipelines",
            site="Come By Chance",
            code=Code.API_1104,
            procedure_number="RT-001",
            opened_on=OPENED,
        ),
    )


def a_weld(work, job, weld="GW-104"):
    add_weld(
        work,
        job,
        NewWeld(
            weld=WeldId.parse(weld),
            joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
            weld_length=mm(529),
            welder="w-19",
            welded_on=OPENED,
        ),
    )


def shoot(work, job, weld="GW-104", index=1, visible=True):
    return record_shot(
        work,
        job,
        ShotRecord(
            weld=WeldId.parse(weld),
            exposure_index=index,
            exposed_at=SHOT,
            technique_reference="RT-001 rev 1",
            evidence=evidence(visible),
        ),
    ).number


def test_a_finished_job_assembles_complete():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        number = shoot(work, job)
        record_reading(work, ReadingRecord(number, reader(), READ))
        package = assemble(work, job)
        assert package.is_complete
        assert package.faults == ()
        assert package.client == "Maritime Pipelines"
        assert package.code is Code.API_1104


def test_an_empty_job_is_not_finishable():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        package = assemble(work, job)
        assert not package.is_complete
        assert "the job has no welds on it" in package.faults


def test_a_weld_with_no_films_is_named_in_the_faults():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        faults = assemble(work, job).faults
        assert "GW-104 has no radiographs" in faults
        assert "GW-104 has no disposition" in faults


def test_an_unread_film_stops_the_package():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        number = shoot(work, job)
        assert f"{number} has not been read" in assemble(work, job).faults


def test_an_inadmissible_film_stops_it_too():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        number = shoot(work, job, visible=False)
        assert f"{number} is not admissible" in assemble(work, job).faults


def test_the_radiograph_schedule_lists_every_film_in_order():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        for index in (2, 1, 3):
            number = shoot(work, job, index=index)
            record_reading(work, ReadingRecord(number, reader(), READ))
        package = assemble(work, job)
        assert [line.exposure_index for line in package.radiographs] == [1, 2, 3]
        assert all(line.weld == "GW-104" for line in package.radiographs)


def test_the_summary_and_the_detail_agree():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job, "GW-104")
        a_weld(work, job, "GW-110")
        for weld in ("GW-104", "GW-110"):
            number = shoot(work, job, weld=weld)
            record_reading(work, ReadingRecord(number, reader(), READ))
        package = assemble(work, job)
        assert package.counts_agree()
        assert len(package.welds) == 2
        assert len(package.radiographs) == 2


def test_a_weld_carries_its_disposition_and_the_clauses_behind_it():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        number = shoot(work, job)
        record_reading(
            work,
            ReadingRecord(number, reader(), READ, (Indication(Kind.ESI, mm(10), mm(60), mm(1)),)),
        )
        line = assemble(work, job).welds[0]
        assert line.disposition == "repair"
        assert line.clauses == ("9.3.8",)


def test_a_clean_weld_reads_as_accepted():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        a_weld(work, job)
        number = shoot(work, job)
        record_reading(work, ReadingRecord(number, reader(), READ))
        assert assemble(work, job).welds[0].disposition == "accept"


def test_the_repair_rate_counts_welds_not_repairs():
    made = factory()
    with transaction(made) as work:
        job = a_job(work)
        for weld in ("GW-104", "GW-104R1", "GW-110", "GW-111", "GW-112"):
            a_weld(work, job, weld)
            number = shoot(work, job, weld=weld)
            record_reading(work, ReadingRecord(number, reader(), READ))
        package = assemble(work, job)
        assert package.welds_shot == 5
        assert package.welds_repaired == 1
        assert package.repair_rate() == Decimal(1) / Decimal(5)


def test_a_job_with_nothing_shot_has_no_repair_rate_rather_than_a_crash():
    made = factory()
    with transaction(made) as work:
        assert assemble(work, a_job(work)).repair_rate() == Decimal(0)


def test_a_job_that_is_not_there_is_refused():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        assemble(work, JobNumber(24, 99))


def test_the_package_carries_the_dates_the_job_was_open():
    made = factory()
    with transaction(made) as work:
        package = assemble(work, a_job(work))
        assert package.opened_on == OPENED
        assert package.closed_on is None
