from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.noise import (
    REFERENCE_RESOLUTION,
    NoiseMeasurement,
    denormalise,
    meets_requirement,
    normalise,
    required_normalised_snr,
    shortfall,
)
from qu_imager.imaging.wire_selection import TechniqueClass
from qu_imager.quantities.length import Length, mm

A = TechniqueClass.A
B = TechniqueClass.B


def measurement(mean="12000", noise="100", resolution="0.0886"):
    return NoiseMeasurement(
        label="parent metal",
        mean_grey=Decimal(mean),
        standard_deviation=Decimal(noise),
        resolution=mm(resolution),
    )


def test_signal_to_noise_is_the_mean_over_the_deviation():
    assert measurement().signal_to_noise == Decimal(120)


def test_a_system_at_the_reference_resolution_needs_no_scaling():
    assert measurement().resolution == REFERENCE_RESOLUTION
    assert abs(measurement().normalised - Decimal(120)) < Decimal("0.01")


def test_a_coarse_system_is_scaled_down():
    coarse = measurement(resolution="0.200")
    assert coarse.signal_to_noise == Decimal(120)
    assert coarse.normalised < Decimal(90)


def test_a_fine_system_is_scaled_up():
    fine = measurement(resolution="0.040")
    assert fine.normalised > Decimal(170)


def test_a_coarse_panel_flattering_itself_is_caught_by_the_normalisation():
    coarse = NoiseMeasurement("a", Decimal(20000), Decimal(100), mm("0.200"))
    fine = NoiseMeasurement("b", Decimal(12000), Decimal(100), mm("0.050"))
    assert coarse.signal_to_noise > fine.signal_to_noise
    assert coarse.normalised < fine.normalised


def test_normalising_and_back_again_returns_the_raw_figure():
    raw = Decimal(120)
    resolution = mm("0.160")
    assert abs(denormalise(normalise(raw, resolution), resolution) - raw) < Decimal("0.0001")


def test_the_scaling_needs_real_numbers():
    with pytest.raises(ValidationError):
        normalise(Decimal(0), mm("0.1"))
    with pytest.raises(ValidationError):
        normalise(Decimal(120), Length.ZERO)
    with pytest.raises(ValidationError):
        denormalise(Decimal(0), mm("0.1"))
    with pytest.raises(ValidationError):
        denormalise(Decimal(120), Length.ZERO)


def test_a_measurement_needs_a_label_a_signal_and_some_noise():
    with pytest.raises(ValidationError):
        NoiseMeasurement("  ", Decimal(1000), Decimal(10), mm("0.1"))
    with pytest.raises(ValidationError):
        NoiseMeasurement("a", Decimal(0), Decimal(10), mm("0.1"))
    with pytest.raises(ValidationError):
        NoiseMeasurement("a", Decimal(1000), Decimal(0), mm("0.1"))
    with pytest.raises(ValidationError):
        NoiseMeasurement("a", Decimal(1000), Decimal(10), Length.ZERO)


def test_the_requirement_rises_with_thickness():
    floors = [required_normalised_snr(mm(t), A) for t in (5, 15, 30, 80, 200)]
    assert floors == [70, 80, 90, 100, 110]


def test_class_b_asks_for_more_at_every_thickness():
    for thickness in (5, 15, 30, 80, 200):
        assert required_normalised_snr(mm(thickness), B) > required_normalised_snr(mm(thickness), A)


def test_the_bands_are_closed_at_the_top():
    assert required_normalised_snr(mm(10), A) == 70
    assert required_normalised_snr(mm("10.1"), A) == 80


def test_the_table_runs_out():
    with pytest.raises(TechniqueError) as caught:
        required_normalised_snr(mm(300), A)
    assert caught.value.context["technique_class"] == "A"
    with pytest.raises(ValidationError):
        required_normalised_snr(Length.ZERO, A)


def test_an_image_that_clears_the_floor_has_no_shortfall():
    good = measurement(mean="12000", noise="100")
    assert meets_requirement(good, mm(10), A)
    assert shortfall(good, mm(10), A) == Decimal(0)


def test_an_image_under_the_floor_reports_how_far_under():
    thin = measurement(mean="6000", noise="100")
    assert not meets_requirement(thin, mm(10), B)
    gap = shortfall(thin, mm(10), B)
    assert Decimal(39) < gap < Decimal(41)


def test_the_same_image_can_pass_class_a_and_fail_class_b():
    borderline = measurement(mean="8500", noise="100")
    assert meets_requirement(borderline, mm(10), A)
    assert not meets_requirement(borderline, mm(10), B)


def test_a_measurement_prints_both_figures():
    text = str(measurement())
    assert "snr 120.0" in text
    assert "normalised 120.0" in text
