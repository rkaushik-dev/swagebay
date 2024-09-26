from datetime import date, datetime

import pytest
from sqlalchemy import inspect

from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.identity import JobNumber
from qu_imager.persistence.models import JobRow, SourceRow
from qu_imager.persistence.numbering import RADIOGRAPH
from qu_imager.persistence.unit_of_work import (
    UnitOfWork,
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm

OPENED = date(2024, 9, 2)


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def job_row(serial=417):
    return JobRow(
        year=24,
        serial=serial,
        client="c",
        site="s",
        acceptance_code="API 1104",
        procedure_number="RT-001",
        opened_on=OPENED,
    )


def source_row(serial="IR-5512"):
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
    )


def test_work_committed_inside_the_block_is_there_afterwards():
    made = factory()
    with transaction(made) as work:
        work.jobs.add(job_row())
    with transaction(made) as work:
        assert work.jobs.get(JobNumber.parse("24-0417")).client == "c"


def test_an_exception_inside_the_block_rolls_the_whole_thing_back():
    made = factory()
    with pytest.raises(RuntimeError), transaction(made) as work:
        work.jobs.add(job_row())
        work.flush()
        raise RuntimeError("the crew went home")
    with transaction(made) as work:
        assert work.jobs.find(JobNumber.parse("24-0417")) is None


def test_a_number_taken_and_then_rolled_back_is_not_lost():
    made = factory()
    with pytest.raises(RuntimeError), transaction(made) as work:
        work.numbering.take(RADIOGRAPH, "24-0417")
        raise RuntimeError("no")
    with transaction(made) as work:
        assert work.numbering.take(RADIOGRAPH, "24-0417") == 1


def test_the_number_and_the_work_it_numbers_commit_together():
    made = factory()
    with transaction(made) as work:
        work.jobs.add(job_row())
        number = work.numbering.take_radiograph_number("24-0417")
    assert number == "24-0417-R0001"
    with transaction(made) as work:
        assert work.numbering.peek(RADIOGRAPH, "24-0417") == 2
        assert work.jobs.find(JobNumber.parse("24-0417")) is not None


def test_every_repository_shares_the_one_session():
    made = factory()
    with transaction(made) as work:
        work.sources.add(source_row())
        work.jobs.add(job_row())
        work.flush()
        assert work.sources.get("IR-5512").id is not None
        assert work.welds.session is work.sources.session


def test_an_explicit_rollback_undoes_the_work_so_far():
    made = factory()
    with transaction(made) as work:
        work.jobs.add(job_row(417))
        work.flush()
        work.rollback()
        work.jobs.add(job_row(418))
    with transaction(made) as work:
        assert work.jobs.find(JobNumber.parse("24-0417")) is None
        assert work.jobs.find(JobNumber.parse("24-0418")) is not None


def test_a_unit_of_work_that_was_never_opened_refuses_to_be_used():
    work = UnitOfWork(factory())
    for call in (work.commit, work.rollback, work.flush):
        with pytest.raises(ConflictError):
            call()


def test_the_repositories_are_not_optional_once_the_block_is_entered():
    made = factory()
    with transaction(made) as work:
        assert work.is_open
        assert work.jobs.session is work.session
        assert work.welds.session is work.session
        assert work.sources.session is work.session


def test_leaving_the_block_closes_the_repositories_too():
    work = UnitOfWork(factory())
    with work:
        assert work.is_open
        assert work.jobs is not None
    assert not work.is_open
    for reach in ("session", "jobs", "welds", "sources", "numbering"):
        with pytest.raises(ConflictError):
            getattr(work, reach)


def test_a_lookup_that_misses_still_raises_inside_a_unit_of_work():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        work.sources.get("IR-9999")


def test_the_schema_builder_makes_every_table():
    engine = build_engine()
    create_schema(engine)
    names = set(inspect(engine).get_table_names())
    assert {"job", "weld", "radiograph", "source", "counter"} <= names


def test_a_second_unit_of_work_sees_the_first_one_committed():
    made = factory()
    with transaction(made) as first:
        first.sources.add(source_row())
    with transaction(made) as second:
        assert second.sources.get("IR-5512").serial == "IR-5512"
