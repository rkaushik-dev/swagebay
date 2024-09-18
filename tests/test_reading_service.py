from datetime import date, datetime
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, NotFoundError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.assessment import Disposition
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import WeldId
from qu_imager.imaging.admissibility import Evidence, IndicatorEvidence
from qu_imager.imaging.density import Calibration, Reading, grade
from qu_imager.imaging.film import film as film_system
from qu_imager.imaging.indicators import HoleIndicator, HoleSize
from qu_imager.imaging.system import ImagingSystem
from qu_imager.imaging.wire_selection import TechniqueClass
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.personnel import Level, LicenceRole, Person
from qu_imager.quantities.length import mm
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.reading import (
    ReadingRecord,
    readers_available,
    readings_outstanding,
    record_reading,
    weld_verdict,
)
from qu_imager.services.shooting import ShotRecord, record_shot

OPENED = date(2024, 9, 23)
SHOT = datetime(2024, 9, 24, 23, 30)
READ = datetime(2024, 9, 25, 8, 0)
WELD = WeldId.parse("GW-104")
ON_FILM = ImagingSystem.from_film(film_system("d7"))


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def reader(badge="0142", level=Level.LEVEL_II, vision=date(2024, 6, 1)):
    return Person(
        badge=badge,
        name="reader",
        level=level,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=date(2023, 1, 15),
        near_vision_on=vision,
        colour_perception_on=vision,
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


def prepared(work, shots=1, visible=True):
    job = open_job(
        work,
        NewJob(
            client="c",
            site="s",
            code=Code.API_1104,
            procedure_number="RT-001",
            opened_on=OPENED,
        ),
    )
    add_weld(
        work,
        job,
        NewWeld(
            weld=WELD,
            joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
            weld_length=mm(529),
            welder="w-19",
            welded_on=OPENED,
        ),
    )
    numbers = []
    for index in range(1, shots + 1):
        numbers.append(
            record_shot(
                work,
                job,
                ShotRecord(
                    weld=WELD,
                    exposure_index=index,
                    exposed_at=SHOT,
                    technique_reference="RT-001 rev 1",
                    evidence=evidence(visible),
                ),
            ).number
        )
    return job, numbers


def test_a_reading_is_stored_against_the_film():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        stored = work.welds.radiograph(numbers[0])
        assert stored.read_by == "0142"
        assert stored.read_at == READ


def test_indications_are_stored_with_the_reading():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        found = (Indication(Kind.ESI, mm(100), mm(20), mm(1)),)
        record_reading(work, ReadingRecord(numbers[0], reader(), READ, found))
        stored = work.welds.radiograph(numbers[0])
        assert len(stored.indications) == 1
        assert stored.indications[0].kind == "ESI"


def test_a_reader_whose_eye_exam_has_lapsed_is_refused():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        stale = reader(vision=date(2022, 1, 1))
        with pytest.raises(ConflictError) as caught:
            record_reading(work, ReadingRecord(numbers[0], stale, READ))
        assert "near vision test" in caught.value.context["lapsed"]


def test_a_level_one_may_not_read():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        with pytest.raises(ConflictError):
            record_reading(work, ReadingRecord(numbers[0], reader(level=Level.LEVEL_I), READ))


def test_a_film_cannot_be_read_twice():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        with pytest.raises(ConflictError) as caught:
            record_reading(work, ReadingRecord(numbers[0], reader("0201"), READ))
        assert caught.value.context["by"] == "0142"


def test_an_inadmissible_film_cannot_be_read_at_all():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work, visible=False)
        with pytest.raises(ConflictError) as caught:
            record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        assert "not evidence of anything" in caught.value.message


def test_a_film_that_does_not_exist_is_refused():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        prepared(work)
        record_reading(work, ReadingRecord("24-0001-R9999", reader(), READ))


def test_a_reading_names_the_film():
    with pytest.raises(ValidationError):
        ReadingRecord("   ", reader(), READ)


def test_there_is_no_verdict_until_every_film_is_read():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work, shots=2)
        assert weld_verdict(work, job, WELD) is None
        record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        assert weld_verdict(work, job, WELD) is None
        record_reading(work, ReadingRecord(numbers[1], reader(), READ))
        assert weld_verdict(work, job, WELD) is not None


def test_a_weld_with_no_films_has_no_verdict():
    made = factory()
    with transaction(made) as work:
        job = open_job(
            work,
            NewJob(
                client="c",
                site="s",
                code=Code.API_1104,
                procedure_number="RT-001",
                opened_on=OPENED,
            ),
        )
        add_weld(
            work,
            job,
            NewWeld(
                weld=WELD,
                joint=ps.girth_weld("6", "80", STEEL),
                weld_length=mm(529),
                welder="w-19",
                welded_on=OPENED,
            ),
        )
        assert weld_verdict(work, job, WELD) is None


def test_a_clean_weld_is_accepted():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work)
        record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        verdict = weld_verdict(work, job, WELD)
        assert verdict.accepted
        assert verdict.code is Code.API_1104


def test_indications_from_two_films_are_judged_together():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work, shots=2)
        record_reading(
            work,
            ReadingRecord(
                numbers[0], reader(), READ, (Indication(Kind.ESI, mm(10), mm(30), mm(1)),)
            ),
        )
        record_reading(
            work,
            ReadingRecord(
                numbers[1], reader(), READ, (Indication(Kind.ESI, mm(60), mm(30), mm(1)),)
            ),
        )
        verdict = weld_verdict(work, job, WELD)
        assert verdict.disposition is Disposition.REPAIR


def test_the_films_still_waiting_for_a_reader_are_listed():
    made = factory()
    with transaction(made) as work:
        job, numbers = prepared(work, shots=2)
        assert readings_outstanding(work, job) == tuple(numbers)
        record_reading(work, ReadingRecord(numbers[0], reader(), READ))
        assert readings_outstanding(work, job) == (numbers[1],)


def test_the_crew_reports_who_can_read_today():
    crew = (
        reader("0142"),
        reader("0201", level=Level.LEVEL_I),
        reader("0303", vision=date(2022, 1, 1)),
    )
    assert [p.badge for p in readers_available(crew, READ.date())] == ["0142"]
