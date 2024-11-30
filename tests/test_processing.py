from datetime import date, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.processing import (
    CONTRAST_TOLERANCE,
    FOG_TOLERANCE,
    SPEED_TOLERANCE,
    Baseline,
    Strip,
    days_since_baseline,
    drifting,
    excursions,
    in_control,
    trend,
)

SET_UP = date(2024, 9, 1)


def baseline():
    return Baseline(SET_UP, Decimal("2.00"), Decimal("3.00"), Decimal("0.18"))


def strip(day=1, speed="2.00", contrast="3.00", fog="0.18"):
    return Strip(
        read_on=SET_UP + timedelta(days=day),
        speed=Decimal(speed),
        contrast=Decimal(contrast),
        fog=Decimal(fog),
        operator="dr",
    )


def test_a_strip_on_the_baseline_is_in_control():
    assert in_control(strip(), baseline())
    assert excursions(strip(), baseline()) == ()


def test_a_small_drift_inside_the_band_is_still_in_control():
    assert in_control(strip(speed="2.10"), baseline())
    assert in_control(strip(contrast="3.15"), baseline())
    assert in_control(strip(fog="0.22"), baseline())


def test_speed_outside_its_band_is_an_excursion():
    found = excursions(strip(speed="2.20"), baseline())
    assert len(found) == 1
    assert found[0].reading == "speed"
    assert found[0].tolerance == SPEED_TOLERANCE


def test_fog_has_the_tightest_band_of_the_three():
    assert FOG_TOLERANCE < SPEED_TOLERANCE < CONTRAST_TOLERANCE
    assert excursions(strip(fog="0.25"), baseline())
    assert not excursions(strip(contrast="3.15"), baseline())


def test_an_excursion_says_which_way_it_went():
    low = excursions(strip(speed="1.80"), baseline())[0]
    high = excursions(strip(speed="2.20"), baseline())[0]
    assert low.direction == "low"
    assert high.direction == "high"
    assert "low of the baseline" in low.describe()


def test_every_reading_out_of_band_is_reported():
    found = excursions(strip(speed="2.30", contrast="3.40", fog="0.30"), baseline())
    assert {e.reading for e in found} == {"speed", "contrast", "fog"}


def test_a_strip_is_compared_to_the_setup_and_not_to_yesterday():
    assert in_control(strip(day=30, speed="1.87"), baseline())
    assert not in_control(strip(day=31, speed="1.80"), baseline())


def test_the_trend_reads_oldest_first():
    strips = (strip(day=3, speed="1.98"), strip(day=1, speed="2.02"), strip(day=2, speed="2.00"))
    assert trend(strips, baseline()) == (Decimal("0.02"), Decimal("0.00"), Decimal("-0.02"))


def test_a_run_all_on_one_side_is_a_drift_even_inside_the_band():
    strips = tuple(strip(day=d, speed="1.98") for d in range(1, 6))
    assert all(in_control(s, baseline()) for s in strips)
    assert drifting(strips, baseline())


def test_a_run_that_crosses_the_baseline_is_not_a_drift():
    speeds = ["1.98", "2.02", "1.98", "2.02", "1.98"]
    strips = tuple(strip(day=d, speed=s) for d, s in enumerate(speeds, 1))
    assert not drifting(strips, baseline())


def test_a_short_run_cannot_be_called_a_drift():
    strips = tuple(strip(day=d, speed="1.98") for d in range(1, 4))
    assert not drifting(strips, baseline())
    assert drifting(strips, baseline(), run=3)


def test_a_run_of_one_is_refused():
    with pytest.raises(ValidationError):
        drifting((strip(),), baseline(), run=1)


def test_a_strip_needs_a_signature_and_believable_readings():
    with pytest.raises(ValidationError):
        Strip(SET_UP, Decimal(2), Decimal(3), Decimal("0.18"), "  ")
    with pytest.raises(ValidationError) as caught:
        Strip(SET_UP, Decimal(0), Decimal(3), Decimal("0.18"), "dr")
    assert caught.value.context["reading"] == "speed"


def test_the_age_of_the_baseline_is_reported():
    assert days_since_baseline(strip(day=30), baseline()) == 30
