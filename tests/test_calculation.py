from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.exposure.calculation import (
    MINIMUM_PRACTICAL,
    activity_needed,
    exposure_time,
    plan_exposure,
    total_for_exposures,
)
from qu_imager.exposure.catalogue import chart_for
from qu_imager.exposure.corrections import CorrectionChain, distance_correction
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.duration import Duration, minutes, seconds
from qu_imager.quantities.length import inch


def working_chart():
    return chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))


def test_thirty_curie_minutes_on_a_thirty_curie_source_is_a_minute():
    assert exposure_time(Decimal(30), curie(30)) == minutes(1)
    assert exposure_time(Decimal(60), curie(30)) == minutes(2)
    assert exposure_time(Decimal(15), curie(30)) == seconds(30)


def test_the_timer_is_rounded_up_to_a_whole_second():
    span = exposure_time(Decimal("30.1"), curie(30))
    assert span == seconds(61)
    assert span.as_timer() == "1:01"


def test_an_empty_pig_cannot_make_an_exposure():
    with pytest.raises(TechniqueError):
        exposure_time(Decimal(30), Activity.ZERO)
    with pytest.raises(ValidationError):
        exposure_time(Decimal(0), curie(30))


def test_a_plain_shot_off_the_chart_at_the_chart_distance():
    result = plan_exposure(working_chart(), inch(1), CorrectionChain(), curie(60))
    assert result.chart_reading == Decimal(30)
    assert result.corrected_reading == Decimal(30)
    assert result.exposure == seconds(30)
    assert result.is_practical


def test_the_result_carries_the_whole_arithmetic():
    chain = CorrectionChain().with_correction(distance_correction(inch(24), inch(36)))
    result = plan_exposure(working_chart(), inch(1), chain, curie(60))
    assert result.chart_key == "ir192-steel-d7-2.0"
    assert result.corrections.names() == ("distance",)
    assert result.corrected_reading == Decimal(30) * Decimal("2.25")
    assert result.exposure == seconds(68)


def test_backing_off_lengthens_the_shot_by_the_square():
    close = plan_exposure(working_chart(), inch(1), CorrectionChain(), curie(60))
    chain = CorrectionChain().with_correction(distance_correction(inch(24), inch(48)))
    far = plan_exposure(working_chart(), inch(1), chain, curie(60))
    assert far.exposure.ms == close.exposure.ms * 4


def test_a_shot_under_ten_seconds_is_flagged_rather_than_printed():
    result = plan_exposure(working_chart(), inch("0.25"), CorrectionChain(), curie(120))
    assert result.exposure < MINIMUM_PRACTICAL
    assert not result.is_practical
    assert "ten second floor" in result.warnings[0]


def test_a_shot_that_does_not_fit_the_window_is_flagged():
    result = plan_exposure(
        working_chart(), inch(2), CorrectionChain(), curie(20), window=minutes(10)
    )
    assert not result.is_practical
    assert "window" in result.warnings[0]


def test_a_shot_that_fits_the_window_is_not_flagged():
    result = plan_exposure(
        working_chart(), inch(1), CorrectionChain(), curie(60), window=minutes(10)
    )
    assert result.warnings == ()


def test_both_bounds_can_fire_at_once_only_when_they_really_do():
    result = plan_exposure(
        working_chart(), inch("0.25"), CorrectionChain(), curie(120), window=minutes(10)
    )
    assert len(result.warnings) == 1


def test_the_description_is_what_goes_on_the_technique_sheet():
    result = plan_exposure(working_chart(), inch(1), CorrectionChain(), curie(60))
    text = result.describe()
    assert "ir192-steel-d7-2.0" in text
    assert "25.40 mm" in text
    assert "no corrections" in text
    assert "0:30" in text


def test_a_thickness_off_the_chart_refuses_before_any_division():
    with pytest.raises(TechniqueError):
        plan_exposure(working_chart(), inch(4), CorrectionChain(), curie(60))


def test_working_backwards_to_the_source_a_shot_would_need():
    needed = activity_needed(Decimal(30), minutes(1))
    assert needed == curie(30)
    assert activity_needed(Decimal(30), minutes(2)) == curie(15)
    with pytest.raises(ValidationError):
        activity_needed(Decimal(30), Duration.ZERO)
    with pytest.raises(ValidationError):
        activity_needed(Decimal(0), minutes(1))


def test_the_source_is_out_for_the_whole_joint_not_one_shot():
    assert total_for_exposures(minutes(1), 3) == minutes(3)
    assert total_for_exposures(seconds(30), 2) == minutes(1)
    with pytest.raises(ValidationError):
        total_for_exposures(minutes(1), 0)
