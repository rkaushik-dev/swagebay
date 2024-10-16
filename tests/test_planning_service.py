from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import NotFoundError, TechniqueError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.duration import minutes
from qu_imager.quantities.length import mm
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.planning import PlanRequest, compare_films, fastest_workable, plan
from qu_imager.services.sources import register_source
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import LeakTest, SealedSource, SourceCertificate

ASSAY = datetime(2024, 10, 1)
SHOT = ASSAY + timedelta(days=20)
OPENED = date(2024, 10, 1)
WELD = WeldId.parse("GW-104")


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def a_source(serial="IR-5512", wiped=True):
    source = SealedSource(
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
    if wiped:
        source.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return source


def a_job(work):
    return open_job(
        work,
        NewJob(
            client="c",
            site="s",
            code=Code.API_1104,
            procedure_number="RT-001",
            opened_on=OPENED,
        ),
    )


def a_weld(nominal="6", schedule="80"):
    return NewWeld(
        weld=WELD,
        joint=ps.girth_weld(nominal, schedule, STEEL, reinforcement=mm(2)),
        weld_length=mm(529),
        welder="w-19",
        welded_on=OPENED,
    )


def prepared(work, nominal="6", schedule="80", wiped=True):
    register_source(work, a_source(wiped=wiped))
    job = a_job(work)
    add_weld(work, job, a_weld(nominal, schedule))
    return job


def a_request(film="d7", shot=ShotType.DWSI, **kwargs):
    fields = {
        "weld": WELD,
        "source_serial": "IR-5512",
        "film_key": film,
        "shot_at": SHOT,
        "shot": shot,
    }
    fields.update(kwargs)
    return PlanRequest(**fields)


def test_a_sheet_is_planned_from_what_is_on_the_record():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        sheet = plan(work, job, a_request())
        assert sheet.shot is ShotType.DWSI
        assert sheet.penetrated == mm("25.94")
        assert sheet.is_clean


def test_the_wall_comes_off_the_weld_and_nobody_retypes_it():
    made = factory()
    with transaction(made) as work:
        job = prepared(work, schedule="40")
        assert plan(work, job, a_request()).penetrated == mm("18.22")


def test_the_source_is_decayed_to_the_night_of_the_shot():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        early = plan(work, job, a_request())
        late = plan(work, job, a_request(shot_at=SHOT + timedelta(days=90)))
        assert late.exposure.exposure > early.exposure.exposure


def test_a_slower_film_gives_a_longer_sheet():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        fast = plan(work, job, a_request(film="d7"))
        slow = plan(work, job, a_request(film="d4"))
        assert slow.exposure.exposure > fast.exposure.exposure


def test_the_indicator_side_travels_through_to_the_sheet():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        sheet = plan(work, job, a_request(indicator_side=IndicatorSide.FILM))
        assert sheet.indicator.needs_f_marker


def test_a_sheet_with_warnings_still_comes_back():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        sheet = plan(work, job, a_request(window=minutes(1)))
        assert not sheet.is_clean
        assert sheet.warnings


def test_a_weld_that_is_not_there_is_refused():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        job = prepared(work)
        plan(work, job, a_request(weld=WeldId.parse("GW-999")))


def test_a_source_that_is_not_there_is_refused():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        job = prepared(work)
        plan(work, job, a_request(source_serial="IR-9999"))


def test_a_job_that_is_not_there_is_refused_too():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        prepared(work)
        plan(work, JobNumber(24, 99), a_request())


def test_a_request_names_a_source_and_a_film():
    with pytest.raises(ValidationError):
        PlanRequest(weld=WELD, source_serial="  ", film_key="d7", shot_at=SHOT)
    with pytest.raises(ValidationError):
        PlanRequest(weld=WELD, source_serial="IR-5512", film_key="  ", shot_at=SHOT)


def test_a_request_moved_onto_another_film_keeps_everything_else():
    original = a_request(window=minutes(30), indicator_side=IndicatorSide.FILM)
    moved = original.on_film("d4")
    assert moved.film_key == "d4"
    assert moved.window == original.window
    assert moved.indicator_side == original.indicator_side
    assert moved.shot == original.shot


def test_several_films_are_compared_side_by_side():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        sheets = compare_films(work, job, a_request(), ("d4", "d5", "d7"))
        assert set(sheets) == {"d4", "d5", "d7"}
        assert sheets["d4"].exposure.exposure > sheets["d7"].exposure.exposure


def test_comparing_nothing_is_refused():
    made = factory()
    with pytest.raises(ValidationError), transaction(made) as work:
        job = prepared(work)
        compare_films(work, job, a_request(), ())


def test_the_fastest_workable_film_is_the_quickest_clean_one():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        assert fastest_workable(work, job, a_request(), ("d4", "d5", "d7")) == "d7"


def test_nothing_workable_comes_back_as_nothing():
    made = factory()
    with transaction(made) as work:
        job = prepared(work)
        assert fastest_workable(work, job, a_request(window=minutes(1)), ("d4", "d7")) is None


def test_a_wall_off_the_end_of_the_chart_still_refuses():
    made = factory()
    with transaction(made) as work:
        register_source(work, a_source())
        job = a_job(work)
        add_weld(work, job, a_weld("24", "160"))
        with pytest.raises(TechniqueError):
            plan(work, job, a_request())


def test_an_unwiped_source_shows_up_as_a_warning_on_the_sheet():
    made = factory()
    with transaction(made) as work:
        job = prepared(work, wiped=False)
        assert any("leak test" in w for w in plan(work, job, a_request()).warnings)
