from datetime import datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.density import (
    STEP_WEDGE_TOLERANCE,
    Calibration,
    Reading,
    grade,
)

CHECKED = datetime(2024, 5, 6, 6, 0)
READ = datetime(2024, 5, 6, 9, 0)


def calibration(error="0.02", checked=CHECKED):
    return Calibration(
        checked_at=checked,
        step_nominal=Decimal("2.00"),
        step_read=Decimal("2.00") + Decimal(error),
        instrument="X-Rite 301",
    )


def readings(indicator="2.50", left="2.40", right="2.60"):
    return (
        Reading("through the indicator", Decimal(indicator), through_indicator=True),
        Reading("weld left", Decimal(left)),
        Reading("weld right", Decimal(right)),
    )


def test_a_sound_set_of_readings_passes():
    verdict = grade(readings(), gamma=True, calibration=calibration(), read_at=READ)
    assert verdict.passed
    assert verdict.reasons == ()
    assert verdict.indicator_density == Decimal("2.50")
    assert verdict.describe() == "density acceptable"


def test_the_variation_window_comes_back_with_the_verdict():
    verdict = grade(readings(), gamma=True, calibration=calibration(), read_at=READ)
    assert verdict.low == Decimal("2.125")
    assert verdict.high == Decimal("3.25")


def test_a_thin_film_fails_the_floor_on_gamma():
    verdict = grade(
        readings(indicator="1.90", left="1.85", right="1.95"),
        gamma=True,
        calibration=calibration(),
        read_at=READ,
    )
    assert not verdict.passed
    assert len(verdict.reasons) == 3
    assert "below the floor of 2.0" in verdict.reasons[0]


def test_the_same_film_would_have_passed_on_x_rays():
    verdict = grade(
        readings(indicator="1.90", left="1.85", right="1.95"),
        gamma=False,
        calibration=calibration(),
        read_at=READ,
    )
    assert verdict.passed


def test_a_dense_film_fails_the_ceiling():
    verdict = grade(
        readings(indicator="4.10", left="4.05", right="4.15"),
        gamma=True,
        calibration=calibration(),
        read_at=READ,
    )
    assert not verdict.passed
    assert all("above the ceiling" in r for r in verdict.reasons)


def test_a_reading_inside_the_band_can_still_fail_the_variation_rule():
    verdict = grade(
        readings(indicator="3.20", left="2.10", right="3.10"),
        gamma=True,
        calibration=calibration(),
        read_at=READ,
    )
    assert not verdict.passed
    assert any("outside the variation window" in r for r in verdict.reasons)
    assert not any("floor" in r for r in verdict.reasons)


def test_a_single_reading_cannot_be_checked_for_variation():
    single = (Reading("middle", Decimal("2.50"), through_indicator=True),)
    verdict = grade(single, gamma=True, calibration=calibration(), read_at=READ)
    assert verdict.passed


def test_readings_with_no_indicator_reading_are_refused_on_that_ground():
    plain = (Reading("middle", Decimal("2.50")), Reading("end", Decimal("2.55")))
    verdict = grade(plain, gamma=True, calibration=calibration(), read_at=READ)
    assert not verdict.passed
    assert "exactly one reading has to be taken through the indicator" in verdict.reasons


def test_two_indicator_readings_are_refused_too():
    two = (
        Reading("a", Decimal("2.50"), through_indicator=True),
        Reading("b", Decimal("2.55"), through_indicator=True),
    )
    verdict = grade(two, gamma=True, calibration=calibration(), read_at=READ)
    assert not verdict.passed
    assert verdict.indicator_density is None


def test_a_composite_view_has_a_much_lower_floor():
    thin = readings(indicator="1.40", left="1.35", right="1.45")
    assert not grade(thin, gamma=True, calibration=calibration(), read_at=READ).passed
    assert grade(thin, gamma=True, composite=True, calibration=calibration(), read_at=READ).passed


def test_a_missing_calibration_is_itself_a_reason():
    verdict = grade(readings(), gamma=True)
    assert not verdict.passed
    assert "no densitometer calibration on record" in verdict.reasons


def test_a_stale_calibration_is_a_reason():
    stale = calibration(checked=CHECKED - timedelta(days=1))
    verdict = grade(readings(), gamma=True, calibration=stale, read_at=READ)
    assert not verdict.passed
    assert "densitometer calibration is not current" in verdict.reasons


def test_a_failed_step_wedge_check_is_never_current():
    bad = calibration(error="0.09")
    assert not bad.passed
    assert abs(bad.error) > STEP_WEDGE_TOLERANCE
    assert not bad.is_current_at(READ)
    assert calibration(error="0.05").passed


def test_a_calibration_covers_one_shift_and_no_more():
    check = calibration()
    assert check.is_current_at(CHECKED)
    assert check.is_current_at(CHECKED + timedelta(hours=7, minutes=59))
    assert not check.is_current_at(CHECKED + timedelta(hours=8))
    assert not check.is_current_at(CHECKED - timedelta(minutes=1))


def test_a_reading_needs_a_label_and_a_believable_number():
    with pytest.raises(ValidationError):
        Reading("  ", Decimal("2.5"))
    with pytest.raises(ValidationError):
        Reading("a", Decimal(0))
    with pytest.raises(ValidationError):
        Reading("a", Decimal(9))


def test_grading_nothing_is_refused():
    with pytest.raises(ValidationError):
        grade((), gamma=True)
