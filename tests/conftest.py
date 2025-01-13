"""Fixtures the technique tests and everything downstream build on."""

from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.geometry import pipe_schedule as ps
from qu_imager.imaging.film import film
from qu_imager.imaging.system import ImagingSystem
from qu_imager.materials import STEEL
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import LeakTest, SealedSource, SourceCertificate

ASSAY = datetime(2024, 5, 1, tzinfo=UTC)
SHOT_DAY = ASSAY + timedelta(days=20)


@pytest.fixture
def iridium_source():
    certificate = SourceCertificate(
        serial="IR-5512",
        isotope=IR192,
        assay_activity=curie(90),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
    )
    source = SealedSource(certificate=certificate)
    source.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return source


@pytest.fixture
def six_inch_weld():
    return ps.girth_weld("6", "80", STEEL, reinforcement=mm("2.0"))


@pytest.fixture
def twelve_inch_weld():
    return ps.girth_weld("12", "80", STEEL, reinforcement=mm("2.5"))


@pytest.fixture
def on_d7():
    return ImagingSystem.from_film(film("d7"))


@pytest.fixture
def crew():
    from datetime import date

    from qu_imager.personnel import Level, LicenceRole, Person

    reader = Person(
        badge="0142",
        name="lead hand",
        level=Level.LEVEL_II,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=date(2023, 1, 15),
        near_vision_on=date(2024, 1, 10),
        colour_perception_on=date(2024, 1, 10),
    )
    helper = Person(
        badge="0201",
        name="assistant",
        level=Level.LEVEL_I,
        licence_role=LicenceRole.ASSISTANT,
        certified_on=date(2023, 6, 1),
        near_vision_on=date(2024, 1, 10),
        colour_perception_on=date(2024, 1, 10),
    )
    return (reader, helper)


@pytest.fixture
def kitted():
    from datetime import date

    from qu_imager.safety.dosimetry import Assignment, Dosimeter, DosimeterKind

    due = date(2025, 1, 1)

    def kit(badge):
        return Assignment(
            badge=badge,
            dosimeters=(
                Dosimeter(DosimeterKind.BADGE, f"B-{badge}"),
                Dosimeter(DosimeterKind.DIRECT_READING, f"D-{badge}", due),
                Dosimeter(DosimeterKind.ALARMING_RATEMETER, f"A-{badge}", due),
            ),
        )

    return (kit("0142"), kit("0201"))


def database():
    """One throwaway database with the schema in it, per call.

    Eleven test modules had grown their own copy of this three line helper and
    they had drifted: two built the schema before the factory and the rest
    after, which works either way and looks like it matters. One copy.
    """
    from qu_imager.persistence.unit_of_work import (
        build_engine,
        create_schema,
        unit_of_work_factory,
    )

    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def a_reader(badge="0142", level=None, vision=None):
    """A Level II with current eyes, which is what most tests want."""
    from datetime import date

    from qu_imager.personnel import Level, LicenceRole, Person

    return Person(
        badge=badge,
        name="reader",
        level=level or Level.LEVEL_II,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=date(2023, 1, 15),
        near_vision_on=vision or date(2024, 6, 1),
        colour_perception_on=vision or date(2024, 6, 1),
    )


def film_evidence(penetrated_mm=26, visible=True, density="2.50", at=None):
    """A sound piece of film with a visible essential hole.

    The three copies of this had different densities and different plaque
    numbers for no reason anybody could remember, so a test that passed in one
    module and failed in another looked like a bug in the rules rather than a
    difference in the fixture.
    """
    from datetime import datetime
    from decimal import Decimal

    from qu_imager.imaging.admissibility import Evidence, IndicatorEvidence
    from qu_imager.imaging.density import Calibration, Reading, grade
    from qu_imager.imaging.film import film
    from qu_imager.imaging.indicators import HoleIndicator, HoleSize
    from qu_imager.imaging.selection import TechniqueClass
    from qu_imager.imaging.system import ImagingSystem
    from qu_imager.quantities.length import mm as millimetres

    moment = at or datetime(2024, 11, 5, 23, 0)
    return Evidence(
        system=ImagingSystem.from_film(film("d7")),
        penetrated=millimetres(penetrated_mm),
        technique_class=TechniqueClass.A,
        indicator=IndicatorEvidence(
            required_hole=HoleIndicator(30),
            required_hole_size=HoleSize.TWO_T,
            hole_visible=visible,
        ),
        density=grade(
            (
                Reading("indicator", Decimal(density), through_indicator=True),
                Reading("weld", Decimal(density)),
            ),
            gamma=True,
            calibration=Calibration(moment, Decimal("2.00"), Decimal("2.01"), "X-Rite"),
            read_at=moment,
        ),
    )
