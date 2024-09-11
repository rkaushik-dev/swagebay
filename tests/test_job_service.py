from datetime import date, datetime

import pytest

from qu_imager.errors import ConflictError, NotFoundError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import JobNumber, WeldId
from qu_imager.materials import STEEL
from qu_imager.persistence.models import RadiographRow
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.quantities.length import mm
from qu_imager.services.jobs import (
    NewJob,
    NewWeld,
    add_weld,
    close_job,
    open_job,
    outstanding,
)

OPENED = date(2024, 9, 9)


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def a_job(client="Maritime Pipelines"):
    return NewJob(
        client=client,
        site="Come By Chance",
        code=Code.API_1104,
        procedure_number="RT-001",
        opened_on=OPENED,
    )


def a_weld(weld="GW-104", length=529):
    return NewWeld(
        weld=WeldId.parse(weld),
        joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
        weld_length=mm(length),
        welder="w-19",
        welded_on=OPENED,
    )


def film(number="R-001", index=1, admissible=True, read=True):
    return RadiographRow(
        number=number,
        exposure_index=index,
        exposed_at=datetime(2024, 9, 10, 23, 0),
        technique_reference="RT-001 rev 1",
        medium="film",
        admissible=admissible,
        read_by="0142" if read else None,
        read_at=datetime(2024, 9, 11, 8, 0) if read else None,
    )


def test_a_job_gets_the_next_serial_for_its_year():
    made = factory()
    with transaction(made) as work:
        assert open_job(work, a_job()) == JobNumber(24, 1)
        assert open_job(work, a_job("Other")) == JobNumber(24, 2)


def test_the_serial_survives_a_new_transaction():
    made = factory()
    with transaction(made) as work:
        open_job(work, a_job())
    with transaction(made) as work:
        assert open_job(work, a_job()) == JobNumber(24, 2)


def test_a_job_that_rolled_back_does_not_burn_its_serial():
    made = factory()
    with pytest.raises(RuntimeError), transaction(made) as work:
        open_job(work, a_job())
        raise RuntimeError("no")
    with transaction(made) as work:
        assert open_job(work, a_job()) == JobNumber(24, 1)


def test_a_job_needs_a_client_a_site_and_a_procedure():
    for missing in ("client", "site", "procedure_number"):
        with pytest.raises(ValidationError) as caught:
            NewJob(
                client="" if missing == "client" else "c",
                site="" if missing == "site" else "s",
                code=Code.API_1104,
                procedure_number="" if missing == "procedure_number" else "RT-001",
                opened_on=OPENED,
            )
        assert caught.value.context["field"] == missing


def test_a_weld_goes_onto_an_open_job():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        job = work.jobs.get(number)
        assert len(job.welds) == 1
        assert job.welds[0].wall == mm("10.97")


def test_the_same_weld_twice_is_refused():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        with pytest.raises(ConflictError):
            add_weld(work, number, a_weld())


def test_a_repair_round_is_a_different_weld_as_far_as_adding_goes():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld("GW-104"))
        add_weld(work, number, a_weld("GW-104R1"))
        assert len(work.jobs.get(number).welds) == 2


def test_a_weld_needs_a_welder_and_a_length():
    with pytest.raises(ValidationError):
        a_weld(length=0)
    with pytest.raises(ValidationError):
        NewWeld(
            weld=WeldId.parse("GW-104"),
            joint=ps.girth_weld("6", "80", STEEL),
            weld_length=mm(500),
            welder="   ",
            welded_on=OPENED,
        )


def test_a_weld_cannot_be_added_to_a_job_that_does_not_exist():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        add_weld(work, JobNumber(24, 99), a_weld())


def test_an_empty_job_is_outstanding_on_having_no_welds():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        assert "the job has no welds on it" in outstanding(work, number)


def test_a_weld_with_no_radiographs_is_outstanding():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        assert "GW-104 has no radiographs" in outstanding(work, number)


def test_an_unread_or_inadmissible_film_keeps_the_job_open():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        job = work.jobs.get(number)
        job.welds[0].radiographs.append(film("R-001", 1, read=False))
        job.welds[0].radiographs.append(film("R-002", 2, admissible=False))
        work.flush()
        pending = outstanding(work, number)
        assert "R-001 has not been read" in pending
        assert "R-002 is not admissible" in pending


def test_a_finished_job_closes():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        work.jobs.get(number).welds[0].radiographs.append(film())
        work.flush()
        assert outstanding(work, number) == ()
        close_job(work, number, date(2024, 9, 20))
        assert work.jobs.get(number).closed_on == date(2024, 9, 20)


def test_an_unfinished_job_refuses_to_close_and_says_how_many():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        with pytest.raises(ConflictError) as caught:
            close_job(work, number, date(2024, 9, 20))
        assert caught.value.context["outstanding"] == 1


def test_a_job_cannot_close_twice_or_before_it_opened():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        work.jobs.get(number).welds[0].radiographs.append(film())
        work.flush()
        with pytest.raises(ValidationError):
            close_job(work, number, date(2024, 1, 1))
        close_job(work, number, date(2024, 9, 20))
        with pytest.raises(ConflictError):
            close_job(work, number, date(2024, 9, 21))


def test_a_weld_cannot_be_added_to_a_closed_job():
    made = factory()
    with transaction(made) as work:
        number = open_job(work, a_job())
        add_weld(work, number, a_weld())
        work.jobs.get(number).welds[0].radiographs.append(film())
        work.flush()
        close_job(work, number, date(2024, 9, 20))
        with pytest.raises(ConflictError):
            add_weld(work, number, a_weld("GW-105"))
