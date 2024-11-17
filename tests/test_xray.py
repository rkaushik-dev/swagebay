from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.materials import Quality
from qu_imager.quantities.duration import Duration, minutes, seconds
from qu_imager.quantities.length import Length, mm
from qu_imager.sources.xray import (
    Tube,
    known_tubes,
    milliampere_seconds,
    time_for,
    tube,
    tubes_reaching,
)


def test_a_set_carries_its_range_and_its_focal_spot():
    small = tube("eresco42")
    assert small.min_kv == 10
    assert small.max_kv == 200
    assert small.focal_spot == mm("1.0")


def test_a_set_says_what_it_can_reach():
    small = tube("eresco42")
    assert small.can_reach(150)
    assert not small.can_reach(300)
    assert not small.can_reach(5)


def test_a_kilovoltage_outside_the_set_is_refused_with_the_range():
    with pytest.raises(TechniqueError) as caught:
        tube("eresco42").check_kv(300)
    assert caught.value.context["range"] == "10 to 200"


def test_a_crawler_set_has_exactly_one_kilovoltage():
    fixed = tube("xrs3")
    assert fixed.min_kv == fixed.max_kv == 270
    assert fixed.can_reach(270)
    assert not fixed.can_reach(269)


def test_a_current_over_what_the_set_gives_is_refused():
    with pytest.raises(TechniqueError) as caught:
        tube("eresco42").check_current(Decimal(20))
    assert caught.value.context["max_ma"] == "10"
    tube("eresco42").check_current(Decimal(10))


def test_a_current_of_nothing_is_refused_as_a_value_problem():
    with pytest.raises(ValidationError):
        tube("eresco42").check_current(Decimal(0))


def test_the_quantity_knob_scales_in_both_of_its_inputs():
    assert milliampere_seconds(Decimal(5), seconds(60)) == Decimal(300)
    assert milliampere_seconds(Decimal(10), seconds(60)) == Decimal(600)
    assert milliampere_seconds(Decimal(5), minutes(2)) == Decimal(600)


def test_the_time_for_an_exposure_inverts_the_quantity():
    assert time_for(Decimal(5), Decimal(300)) == seconds(60)
    assert time_for(Decimal(10), Decimal(300)) == seconds(30)


def test_the_time_is_rounded_up_to_a_whole_second():
    assert time_for(Decimal(7), Decimal(100)) == seconds(15)


def test_a_quantity_or_a_current_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        milliampere_seconds(Decimal(0), seconds(60))
    with pytest.raises(ValidationError):
        time_for(Decimal(0), Decimal(300))
    with pytest.raises(ValidationError):
        time_for(Decimal(5), Decimal(0))


def test_a_half_duty_set_rests_as_long_as_it_ran():
    assert tube("eresco42").cooling_after(minutes(2)) == minutes(2)


def test_a_low_duty_set_rests_four_times_as_long():
    assert tube("xrs3").cooling_after(minutes(1)) == minutes(4)


def test_a_continuous_set_needs_no_rest():
    assert tube("mg452").cooling_after(minutes(10)) == Duration.ZERO


def test_the_beam_quality_steps_with_the_kilovoltage():
    small = tube("eresco42")
    assert small.quality_at(80) is Quality.KV100
    assert small.quality_at(100) is Quality.KV100
    assert small.quality_at(120) is Quality.KV150
    assert small.quality_at(200) is Quality.KV220


def test_the_quality_lookup_refuses_a_kilovoltage_the_set_cannot_make():
    with pytest.raises(TechniqueError):
        tube("eresco42").quality_at(300)


def test_a_high_kilovoltage_set_reads_the_four_hundred_column():
    assert tube("mg452").quality_at(400) is Quality.KV400
    assert tube("mg452").quality_at(450) is Quality.MV1


def test_the_sets_that_reach_a_kilovoltage_come_back_finest_spot_first():
    fits = tubes_reaching(150)
    assert [t.key for t in fits] == ["eresco42", "eresco65", "mg452"]
    assert tubes_reaching(500) == ()


def test_a_set_with_no_range_or_no_spot_is_refused():
    def build(**overrides):
        base = {
            "key": "x",
            "name": "x",
            "manufacturer": "x",
            "min_kv": 10,
            "max_kv": 200,
            "max_ma": Decimal(5),
            "focal_spot": mm(1),
            "duty_cycle": Decimal("0.5"),
        }
        base.update(overrides)
        return Tube(**base)

    with pytest.raises(ValidationError):
        build(max_kv=5)
    with pytest.raises(ValidationError):
        build(min_kv=0)
    with pytest.raises(ValidationError):
        build(max_ma=Decimal(0))
    with pytest.raises(ValidationError):
        build(focal_spot=Length.ZERO)
    with pytest.raises(ValidationError):
        build(duty_cycle=Decimal("1.5"))


def test_the_catalogue_is_closed():
    assert known_tubes() == ("eresco42", "eresco65", "mg452", "xrs3")
    with pytest.raises(ConfigurationError):
        tube("eresco99")
    assert str(tube("eresco42")) == "ERESCO 42 MF4 10 to 200 kV"
