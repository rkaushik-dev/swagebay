from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.screens import (
    BackscatterTest,
    Filter,
    Screens,
    qualities_with_screens,
    recommended_filter,
    screens_for,
    wrong_screens,
)
from qu_imager.materials import Quality
from qu_imager.quantities.length import Length, mm


def test_a_harder_quality_wants_thicker_lead():
    soft = screens_for(Quality.KV100)
    hard = screens_for(Quality.CO60)
    assert hard.front > soft.front
    assert hard.back > soft.back


def test_a_harder_quality_also_gets_more_out_of_the_screens():
    assert screens_for(Quality.CO60).intensification > screens_for(Quality.KV100).intensification


def test_the_screens_shorten_the_shot_rather_than_lengthening_it():
    for quality in qualities_with_screens():
        assert screens_for(quality).time_factor <= 1


def test_the_iridium_pair_is_the_one_the_shop_uses_most():
    pair = screens_for(Quality.IR192)
    assert pair.front == mm("0.13")
    assert pair.back == mm("0.15")
    assert pair.time_factor == Decimal(1) / Decimal("2.5")


def test_a_quality_with_no_pair_specified_is_refused():
    with pytest.raises(ConfigurationError) as caught:
        screens_for(Quality.MV2)
    assert caught.value.context["quality"] == "2 MV"


def test_a_screen_that_makes_a_shot_longer_is_refused():
    with pytest.raises(ValidationError):
        Screens(Quality.IR192, mm("0.1"), mm("0.1"), Decimal("0.5"))
    with pytest.raises(ValidationError):
        Screens(Quality.IR192, Length(-1), mm("0.1"), Decimal(2))


def test_the_right_pair_on_the_right_shot_draws_no_comment():
    assert wrong_screens(screens_for(Quality.IR192), Quality.IR192) is None


def test_a_thin_pair_on_a_hard_shot_will_fog():
    note = wrong_screens(screens_for(Quality.KV100), Quality.CO60)
    assert note is not None
    assert "thin for Co-60" in note
    assert "fog" in note


def test_a_heavy_pair_on_a_soft_shot_loses_speed():
    note = wrong_screens(screens_for(Quality.CO60), Quality.KV100)
    assert note is not None
    assert "heavy for 100 kV" in note
    assert "lose speed" in note


def test_thin_steel_needs_no_filter():
    assert recommended_filter(mm(10)) is None
    assert recommended_filter(mm(24)) is None


def test_medium_steel_takes_half_a_millimetre_of_copper():
    fitted = recommended_filter(mm(30))
    assert fitted is not None
    assert fitted.material == "copper"
    assert fitted.thickness == mm("0.5")


def test_heavy_steel_takes_twice_that_and_costs_twice_the_time():
    light = recommended_filter(mm(30))
    heavy = recommended_filter(mm(60))
    assert heavy.thickness > light.thickness
    assert heavy.time_multiplier > light.time_multiplier


def test_a_filter_always_makes_the_shot_longer():
    for thickness in (30, 60, 120):
        fitted = recommended_filter(mm(thickness))
        assert fitted.time_multiplier >= 1


def test_a_thickness_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        recommended_filter(Length.ZERO)


def test_a_filter_that_shortens_a_shot_is_refused():
    with pytest.raises(ValidationError):
        Filter("copper", mm(1), Decimal("0.5"))
    with pytest.raises(ValidationError):
        Filter("copper", Length.ZERO, Decimal(2))


def test_no_letter_at_all_means_no_backscatter():
    test = BackscatterTest(letter_visible=False, lighter_than_background=False)
    assert not test.failed
    assert test.verdict() == "no backscatter"


def test_a_lighter_letter_is_the_failure():
    test = BackscatterTest(letter_visible=True, lighter_than_background=True)
    assert test.failed
    assert "shoot it again" in test.verdict()


def test_a_darker_letter_is_not_backscatter():
    test = BackscatterTest(letter_visible=True, lighter_than_background=False)
    assert not test.failed
    assert "not backscatter" in test.verdict()


def test_a_filter_and_a_pair_both_print_for_the_sheet():
    assert recommended_filter(mm(30)).describe() == "0.50 mm copper, x1.4"
    assert screens_for(Quality.IR192).describe() == "0.13 mm front, 0.15 mm back, x2.5 faster"
