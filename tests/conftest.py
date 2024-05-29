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
