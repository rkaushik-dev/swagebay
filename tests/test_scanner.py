from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.computed import plate
from qu_imager.imaging.scanner import (
    Settings,
    achievable_resolution,
    effective_snr,
    faults,
    is_oversampled,
    lowest_workable_gain,
    recommended_pitch,
    resolution_ceiling,
    settings_for,
)
from qu_imager.quantities.length import Length, mm


def settings(pitch="0.050", gain=1, latitude=3):
    return Settings(pitch=mm(pitch), gain=gain, latitude=latitude)


def test_the_resolution_ceiling_is_two_pixels():
    assert resolution_ceiling(settings(pitch="0.050")) == mm("0.100")


def test_a_coarse_scan_throws_away_what_a_fine_plate_held():
    fine = plate("ip1_hd")
    assert fine.basic_spatial_resolution == mm("0.040")
    assert achievable_resolution(fine, settings(pitch="0.100")) == mm("0.200")


def test_a_fine_scan_cannot_improve_on_the_plate():
    fine = plate("ip1_hd")
    assert achievable_resolution(fine, settings(pitch="0.010")) == mm("0.040")


def test_scanning_finer_than_the_plate_holds_is_oversampling():
    fine = plate("ip1_hd")
    assert is_oversampled(fine, settings(pitch="0.010"))
    assert not is_oversampled(fine, settings(pitch="0.020"))
    assert not is_oversampled(fine, settings(pitch="0.100"))


def test_the_recommended_pitch_is_half_the_plate_resolution():
    assert recommended_pitch(plate("ip1_hd")) == mm("0.020")
    assert recommended_pitch(plate("ip5_fast")) == mm("0.065")


def test_gain_one_costs_nothing():
    quiet = settings(gain=1)
    assert quiet.noise_penalty == Decimal(1)
    assert effective_snr(plate("ip1_hd"), quiet) == Decimal(130)


def test_turning_the_gain_up_costs_signal_to_noise():
    fine = plate("ip1_hd")
    assert effective_snr(fine, settings(gain=5)) < effective_snr(fine, settings(gain=1))
    assert effective_snr(fine, settings(gain=10)) < effective_snr(fine, settings(gain=5))


def test_five_gain_steps_cost_about_half_the_signal_to_noise():
    fine = plate("ip1_hd")
    assert abs(effective_snr(fine, settings(gain=5)) - Decimal(65)) < Decimal("0.5")


def test_a_setting_outside_the_scanner_is_refused():
    with pytest.raises(ValidationError):
        settings(gain=0)
    with pytest.raises(ValidationError):
        settings(gain=11)
    with pytest.raises(ValidationError):
        settings(latitude=6)
    with pytest.raises(ValidationError):
        Settings(pitch=Length.ZERO, gain=1, latitude=3)


def test_a_sound_scan_of_a_fine_plate_reports_nothing():
    fine = plate("ip1_hd")
    assert faults(fine, settings(pitch="0.020"), mm("0.050"), 120) == ()


def test_a_coarse_scan_fails_the_resolution_requirement():
    fine = plate("ip1_hd")
    found = faults(fine, settings(pitch="0.100"), mm("0.050"), 120)
    assert any("coarser than" in f for f in found)


def test_a_high_gain_scan_fails_the_noise_requirement():
    fine = plate("ip1_hd")
    found = faults(fine, settings(pitch="0.020", gain=6), mm("0.050"), 120)
    assert any("signal to noise" in f for f in found)
    assert any("at gain 6" in f for f in found)


def test_oversampling_is_reported_even_when_nothing_else_is_wrong():
    fine = plate("ip1_hd")
    found = faults(fine, settings(pitch="0.005"), mm("0.050"), 120)
    assert any("costs time and gains nothing" in f for f in found)


def test_every_fault_is_reported_not_just_the_first():
    fine = plate("ip1_hd")
    found = faults(fine, settings(pitch="0.150", gain=8), mm("0.050"), 120)
    assert len(found) == 2


def test_the_lowest_workable_gain_is_the_one_to_use():
    fine = plate("ip1_hd")
    assert lowest_workable_gain(fine, 110) == 1
    assert lowest_workable_gain(fine, 125) == 1
    assert lowest_workable_gain(fine, 60) == 1


def test_a_coarser_plate_can_afford_more_gain():
    coarse = plate("ip5_fast")
    assert lowest_workable_gain(coarse, 120) >= 1
    assert effective_snr(coarse, settings(gain=1)) > Decimal(200)


def test_a_noise_floor_nothing_reaches_is_refused():
    with pytest.raises(TechniqueError) as caught:
        lowest_workable_gain(plate("ip1_hd"), 500)
    assert caught.value.context["required_snr"] == 500
    with pytest.raises(ValidationError):
        lowest_workable_gain(plate("ip1_hd"), 0)


def test_the_settings_the_office_would_write_down():
    chosen = settings_for(plate("ip1_hd"), 120)
    assert chosen.pitch == mm("0.020")
    assert chosen.gain == 1
    assert chosen.latitude == 3


def test_the_settings_print_for_the_sheet():
    assert settings(pitch="0.020", gain=3).describe() == ("0.020 mm pitch, gain 3, latitude 3")
