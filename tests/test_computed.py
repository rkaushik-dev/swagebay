from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.computed import (
    SCAN_DEADLINE,
    SCAN_WITHOUT_CORRECTION,
    PlateSystem,
    known_plates,
    plate,
    plates_meeting,
)
from qu_imager.quantities.duration import Duration, minutes
from qu_imager.quantities.length import Length, mm


def test_a_plate_carries_both_halves_of_its_characterization():
    fine = plate("ip1_hd")
    assert fine.basic_spatial_resolution == mm("0.040")
    assert fine.normalised_snr == 130
    assert fine.ip_class == "IP1"


def test_a_finer_plate_is_noisier_at_the_same_exposure():
    fine = plate("ip1_hd")
    fast = plate("ip5_fast")
    assert fine.basic_spatial_resolution < fast.basic_spatial_resolution
    assert fine.normalised_snr < fast.normalised_snr


def test_a_plate_scanned_at_once_has_lost_almost_nothing():
    fine = plate("ip1_hd")
    assert fine.remaining_signal(Duration.ZERO) == Decimal(1)
    assert fine.remaining_signal(minutes(10)) > Decimal("0.99")


def test_a_plate_left_a_half_life_has_lost_half_the_image():
    fine = plate("ip1_hd")
    left = fine.remaining_signal(fine.fade_half_life)
    assert abs(left - Decimal("0.5")) < Decimal("1e-20")


def test_a_plate_left_overnight_has_lost_most_of_it():
    fine = plate("ip1_hd")
    assert fine.remaining_signal(Duration.from_hours(12)) < Decimal("0.7")
    assert fine.remaining_signal(Duration.from_hours(40)) < Decimal("0.3")


def test_the_prompt_window_is_half_an_hour():
    fine = plate("ip1_hd")
    assert fine.scan_is_prompt(minutes(29))
    assert fine.scan_is_prompt(SCAN_WITHOUT_CORRECTION)
    assert not fine.scan_is_prompt(minutes(31))


def test_past_the_deadline_the_plate_is_scrapped():
    fine = plate("ip1_hd")
    assert fine.scan_is_usable(Duration.from_hours(7))
    assert fine.scan_is_usable(SCAN_DEADLINE)
    assert not fine.scan_is_usable(Duration.from_hours(9))


def test_a_late_scan_is_still_usable_while_not_being_prompt():
    fine = plate("ip1_hd")
    waited = Duration.from_hours(4)
    assert not fine.scan_is_prompt(waited)
    assert fine.scan_is_usable(waited)


def test_a_slower_plate_holds_its_image_longer():
    assert plate("ip5_fast").fade_half_life > plate("ip1_hd").fade_half_life


def test_a_plate_can_meet_one_half_of_a_specification_and_fail_the_other():
    coarse_but_quiet = plate("ip5_fast")
    assert coarse_but_quiet.meets_noise(200)
    assert not coarse_but_quiet.meets_resolution(mm("0.080"))
    shortfalls = coarse_but_quiet.shortfalls(mm("0.080"), 200)
    assert len(shortfalls) == 1
    assert "coarser" in shortfalls[0]


def test_both_shortfalls_are_reported_when_both_fail():
    fine = plate("ip1_hd")
    shortfalls = fine.shortfalls(mm("0.030"), 200)
    assert len(shortfalls) == 2
    assert "normalised snr 130 is under 200" in shortfalls[1]


def test_a_plate_that_clears_both_reports_nothing():
    assert plate("ip2_std").shortfalls(mm("0.080"), 140) == ()


def test_the_stock_that_meets_a_specification_comes_back_coarsest_first():
    fits = plates_meeting(mm("0.080"), 150)
    assert [p.key for p in fits] == ["ip3_gp", "ip2_std"]
    assert plates_meeting(mm("0.010"), 100) == ()


def test_a_plate_with_no_resolution_or_no_noise_figure_is_refused():
    with pytest.raises(ValidationError):
        PlateSystem("x", "x", "x", "IP1", Length.ZERO, 100, Duration.from_hours(20))
    with pytest.raises(ValidationError):
        PlateSystem("x", "x", "x", "IP1", mm("0.04"), 0, Duration.from_hours(20))
    with pytest.raises(ValidationError):
        PlateSystem("x", "x", "x", "IP1", mm("0.04"), 100, Duration.ZERO)


def test_the_catalogue_is_closed():
    assert known_plates() == ("ip1_hd", "ip2_std", "ip3_gp", "ip5_fast")
    with pytest.raises(ConfigurationError):
        plate("ip9")
    assert str(plate("ip1_hd")) == "HD-IP (IP1)"
