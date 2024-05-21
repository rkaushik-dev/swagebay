from datetime import datetime
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.admissibility import (
    Evidence,
    IndicatorEvidence,
    assess,
    density_is_applicable,
    noise_floor_for,
)
from qu_imager.imaging.computed import plate
from qu_imager.imaging.density import Calibration, Reading, grade
from qu_imager.imaging.film import film
from qu_imager.imaging.indicators import HoleIndicator, HoleSize, WireIndicator
from qu_imager.imaging.noise import NoiseMeasurement
from qu_imager.imaging.system import ImagingSystem
from qu_imager.imaging.wire_selection import TechniqueClass
from qu_imager.quantities.length import mm

CHECKED = datetime(2024, 5, 20, 6, 0)
READ = datetime(2024, 5, 20, 9, 0)
ON_FILM = ImagingSystem.from_film(film("d7"))
ON_PLATE = ImagingSystem.from_plate(plate("ip1_hd"))


def hole_seen(visible=True):
    return IndicatorEvidence(
        required_hole=HoleIndicator(20),
        required_hole_size=HoleSize.TWO_T,
        hole_visible=visible,
    )


def wire_seen(finest=13):
    return IndicatorEvidence(required_wire=WireIndicator(13), finest_wire_visible=finest)


def good_density():
    return grade(
        (
            Reading("indicator", Decimal("2.50"), through_indicator=True),
            Reading("weld", Decimal("2.60")),
        ),
        gamma=True,
        calibration=Calibration(CHECKED, Decimal("2.00"), Decimal("2.01"), "X-Rite"),
        read_at=READ,
    )


def quiet_noise(mean="12000"):
    return NoiseMeasurement("parent", Decimal(mean), Decimal(100), mm("0.040"))


def film_evidence(**overrides):
    base = {
        "system": ON_FILM,
        "penetrated": mm(12),
        "technique_class": TechniqueClass.A,
        "indicator": hole_seen(),
        "density": good_density(),
    }
    base.update(overrides)
    return Evidence(**base)


def plate_evidence(**overrides):
    base = {
        "system": ON_PLATE,
        "penetrated": mm(12),
        "technique_class": TechniqueClass.A,
        "indicator": wire_seen(),
        "noise": quiet_noise(),
    }
    base.update(overrides)
    return Evidence(**base)


def test_a_sound_film_radiograph_is_admissible():
    verdict = assess(film_evidence())
    assert verdict.admissible
    assert verdict.describe() == "admissible"
    assert verdict.gates_run == ("indicator", "density")


def test_the_noise_gate_never_runs_on_film():
    verdict = assess(film_evidence(noise=None))
    assert verdict.admissible
    assert "normalised snr" not in verdict.gates_run


def test_the_density_gate_never_runs_on_a_plate():
    verdict = assess(plate_evidence(density=None))
    assert verdict.admissible
    assert "density" not in verdict.gates_run
    assert not density_is_applicable(ON_PLATE)
    assert density_is_applicable(ON_FILM)


def test_film_with_no_density_readings_fails_on_that():
    verdict = assess(film_evidence(density=None))
    assert not verdict.admissible
    assert "no density readings on record" in verdict.reasons


def test_a_plate_with_no_noise_measurement_fails_on_that():
    verdict = assess(plate_evidence(noise=None))
    assert "no signal to noise measurement on record" in verdict.reasons


def test_an_invisible_essential_hole_sinks_a_radiograph_on_its_own():
    verdict = assess(film_evidence(indicator=hole_seen(visible=False)))
    assert not verdict.admissible
    assert verdict.reasons == ("the 2T hole in the 20 plaque is not visible",)


def test_a_wire_coarser_than_required_sinks_it_too():
    verdict = assess(plate_evidence(indicator=wire_seen(finest=11)))
    assert not verdict.admissible
    assert "the finest wire visible is W11, W13 is required" in verdict.reasons


def test_no_wire_visible_at_all_is_reported_differently():
    evidence = IndicatorEvidence(required_wire=WireIndicator(13), finest_wire_visible=None)
    assert not evidence.satisfied()
    assert "no wire is visible" in evidence.describe_failure()


def test_a_finer_wire_than_required_is_fine():
    assert wire_seen(finest=15).satisfied()
    assert wire_seen(finest=13).satisfied()


def test_every_failure_is_collected_not_just_the_first():
    thin = grade(
        (
            Reading("indicator", Decimal("1.40"), through_indicator=True),
            Reading("weld", Decimal("1.45")),
        ),
        gamma=True,
    )
    verdict = assess(film_evidence(indicator=hole_seen(visible=False), density=thin))
    assert len(verdict.reasons) >= 3
    assert any("hole" in r for r in verdict.reasons)
    assert any("floor" in r for r in verdict.reasons)


def test_a_digital_image_under_the_noise_floor_fails():
    verdict = assess(plate_evidence(technique_class=TechniqueClass.B, noise=quiet_noise("3000")))
    assert not verdict.admissible
    assert "under the floor of 110" in verdict.reasons[0]


def test_the_noise_floor_is_read_off_the_class_and_the_thickness():
    assert noise_floor_for(plate_evidence(technique_class=TechniqueClass.B)) == Decimal(110)
    assert noise_floor_for(plate_evidence()) == Decimal(80)


def test_the_resolution_gate_only_runs_when_a_requirement_is_stated():
    assert assess(plate_evidence()).admissible
    stated = assess(plate_evidence(required_resolution=mm("0.100")))
    assert not stated.admissible
    assert "no duplex gauge reading on record" in stated.reasons


def test_a_duplex_reading_coarser_than_required_fails():
    verdict = assess(plate_evidence(required_resolution=mm("0.100"), duplex_reading=6))
    assert not verdict.admissible
    assert "coarser" in verdict.reasons[0]


def test_a_duplex_reading_that_meets_the_requirement_passes():
    verdict = assess(plate_evidence(required_resolution=mm("0.100"), duplex_reading=10))
    assert verdict.admissible
    assert verdict.gates_run == ("indicator", "normalised snr", "basic spatial resolution")


def test_a_radiograph_is_graded_on_one_family_of_indicator_not_both():
    with pytest.raises(ValidationError):
        IndicatorEvidence()
    with pytest.raises(ValidationError):
        IndicatorEvidence(
            required_hole=HoleIndicator(20),
            required_hole_size=HoleSize.TWO_T,
            required_wire=WireIndicator(13),
        )
    with pytest.raises(ValidationError):
        IndicatorEvidence(required_hole=HoleIndicator(20))
