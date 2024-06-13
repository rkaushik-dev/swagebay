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
