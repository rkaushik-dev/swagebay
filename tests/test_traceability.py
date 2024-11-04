from datetime import date, datetime
from decimal import Decimal

import pytest

from qu_imager.errors import NotFoundError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import WeldId
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
from qu_imager.procedure import Code as ProcedureCode
from qu_imager.procedure import ProcedureRegister, Revision
from qu_imager.quantities.length import mm
from qu_imager.reporting.traceability import (
    assert_traceable,
    broken_chains,
    chain_for,
    chains_for_job,
)
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.reading import ReadingRecord, record_reading
from qu_imager.services.shooting import ShotRecord, record_shot

OPENED = date(2024, 11, 1)
SHOT = datetime(2024, 11, 8, 23, 0)
READ = datetime(2024, 11, 9, 8, 0)
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


def prepared(work, visible=True, read=True):
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
            weld=WeldId.parse("GW-104"),
            joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
            weld_length=mm(529),
            welder="w-19",
            welded_on=OPENED,
        ),
    )
    number = record_shot(
        work,
        job,
        ShotRecord(
            weld=WeldId.parse("GW-104"),
            exposure_index=1,
            exposed_at=SHOT,
            technique_reference="RT-001 rev 1",
            evidence=evidence(visible),
        ),
    ).number
    if read and visible:
        record_reading(work, ReadingRecord(number, reader(), READ))
    return job, number


def register(effective=date(2024, 1, 1)):
    signer = Person(
        badge="0001",
        name="level three",
        level=Level.LEVEL_III,
        licence_role=LicenceRole.SAFETY_OFFICER,
        certified_on=date(2023, 1, 1),
        near_vision_on=date(2024, 1, 1),
        colour_perception_on=date(2024, 1, 1),
    )
    return ProcedureRegister(
        [
            Revision(
                number="RT-001",
                revision=1,
                title="radiography of butt welds",
                code=ProcedureCode.ASME_V,
                effective_on=effective,
                approved_by=signer,
                approved_on=effective,
            )
        ]
    )


def test_a_sound_chain_holds_at_every_link():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        chain = chain_for(work, number)
        assert chain.is_sound
        assert chain.broken() == ()
        assert len(chain.links) == 8


def test_the_chain_names_the_job_the_weld_and_the_welder():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        values = {link.name: link.value for link in chain_for(work, number).links}
        assert values["job"] == "24-0001"
        assert values["weld"] == "GW-104"
        assert values["welder"] == "w-19"
        assert values["technique"] == "RT-001 rev 1"


def test_an_unread_film_breaks_the_reading_link():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work, read=False)
        chain = chain_for(work, number)
        assert not chain.is_sound
        broken = {link.name for link in chain.broken()}
        assert "read by" in broken


def test_an_inadmissible_film_breaks_the_admissibility_link_and_says_why():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work, visible=False)
        chain = chain_for(work, number)
        link = next(link for link in chain.links if link.name == "admissible")
        assert not link.holds
        assert "not visible" in link.note


def test_a_link_prints_its_state_and_its_note():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work, read=False)
        text = chain_for(work, number).describe()
        assert number in text
        assert "[ok]" in text
        assert "[broken]" in text
        assert "not yet read" in text


def test_the_procedure_link_is_unchecked_without_a_register():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        link = next(link for link in chain_for(work, number).links if link.name == "procedure")
        assert link.holds
        assert "not checked" in link.note


def test_the_procedure_link_resolves_the_revision_in_force_that_night():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        link = next(
            link for link in chain_for(work, number, register()).links if link.name == "procedure"
        )
        assert link.holds
        assert link.value == "RT-001 rev 1"


def test_a_procedure_that_was_not_yet_in_force_breaks_the_link():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        later = register(effective=date(2025, 6, 1))
        link = next(
            link for link in chain_for(work, number, later).links if link.name == "procedure"
        )
        assert not link.holds
        assert "no approved revision" in link.note


def test_every_chain_on_a_job_comes_back_in_radiograph_order():
    made = factory()
    with transaction(made) as work:
        job, first = prepared(work)
        second = record_shot(
            work,
            job,
            ShotRecord(
                weld=WeldId.parse("GW-104"),
                exposure_index=2,
                exposed_at=SHOT,
                technique_reference="RT-001 rev 1",
                evidence=evidence(),
            ),
        ).number
        record_reading(work, ReadingRecord(second, reader(), READ))
        chains = chains_for_job(work, 24, 1)
        assert [c.radiograph for c in chains] == sorted([first, second])


def test_the_broken_chains_are_the_ones_worth_reading():
    made = factory()
    with transaction(made) as work:
        job, _first = prepared(work)
        record_shot(
            work,
            job,
            ShotRecord(
                weld=WeldId.parse("GW-104"),
                exposure_index=2,
                exposed_at=SHOT,
                technique_reference="RT-001 rev 1",
                evidence=evidence(),
            ),
        )
        broken = broken_chains(work, 24, 1)
        assert len(broken) == 1
        assert not broken[0].is_sound


def test_a_job_with_everything_sound_reports_no_broken_chains():
    made = factory()
    with transaction(made) as work:
        prepared(work)
        assert broken_chains(work, 24, 1) == ()


def test_asserting_traceability_refuses_a_broken_chain():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work, read=False)
        with pytest.raises(NotFoundError):
            assert_traceable(work, number)


def test_asserting_traceability_returns_a_sound_chain():
    made = factory()
    with transaction(made) as work:
        _job, number = prepared(work)
        assert assert_traceable(work, number).is_sound


def test_a_film_that_does_not_exist_has_no_chain():
    made = factory()
    with pytest.raises(NotFoundError), transaction(made) as work:
        prepared(work)
        chain_for(work, "24-0001-R9999")
