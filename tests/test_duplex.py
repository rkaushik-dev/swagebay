import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.duplex import (
    DuplexPair,
    meets,
    pair_for_resolution,
    pairs,
    resolution_from_reading,
    unsharpness_budget,
)
from qu_imager.quantities.length import Length, mm


def test_a_pair_resolves_to_twice_its_wire_diameter():
    seven = DuplexPair(7)
    assert seven.wire_diameter == mm("0.20")
    assert seven.unsharpness == mm("0.40")


def test_the_basic_spatial_resolution_is_half_the_unsharpness():
    seven = DuplexPair(7)
    assert seven.basic_spatial_resolution == mm("0.20")
    assert seven.basic_spatial_resolution * 2 == seven.unsharpness


def test_a_higher_pair_number_is_a_finer_pair():
    resolutions = [p.basic_spatial_resolution for p in pairs()]
    assert resolutions == sorted(resolutions, reverse=True)
    assert len(pairs()) == 13


def test_the_gauge_runs_from_eight_tenths_down_to_five_hundredths():
    assert DuplexPair(1).wire_diameter == mm("0.80")
    assert DuplexPair(13).wire_diameter == mm("0.05")
    assert str(DuplexPair(4)) == "D4"


def test_a_pair_outside_the_gauge_is_refused():
    with pytest.raises(ConfigurationError):
        DuplexPair(0)
    with pytest.raises(ConfigurationError):
        DuplexPair(14)


def test_a_reading_converts_straight_to_a_resolution():
    assert resolution_from_reading(7) == mm("0.20")
    assert resolution_from_reading(10) == mm("0.10")


def test_the_coarsest_pair_that_meets_a_target_is_the_one_to_ask_for():
    assert pair_for_resolution(mm("0.20")).number == 7
    assert pair_for_resolution(mm("0.25")).number == 6
    assert pair_for_resolution(mm("0.19")).number == 8


def test_a_target_finer_than_the_gauge_is_refused():
    with pytest.raises(ConfigurationError) as caught:
        pair_for_resolution(mm("0.01"))
    assert caught.value.context["target_mm"] == "0.01"
    with pytest.raises(ValidationError):
        pair_for_resolution(Length.ZERO)


def test_meeting_a_requirement_is_a_comparison_not_a_count():
    assert meets(10, mm("0.20"))
    assert meets(7, mm("0.20"))
    assert not meets(6, mm("0.20"))
    assert not meets(1, mm("0.20"))


def test_the_two_unsharpness_terms_add_in_quadrature():
    total = unsharpness_budget(mm("0.30"), mm("0.40"))
    assert total == mm("0.50")


def test_quadrature_is_gentler_than_a_straight_sum():
    quadrature = unsharpness_budget(mm("0.30"), mm("0.40"))
    assert quadrature < mm("0.30") + mm("0.40")


def test_one_term_alone_is_the_whole_budget():
    assert unsharpness_budget(mm("0.40"), Length.ZERO) == mm("0.40")
    assert unsharpness_budget(Length.ZERO, mm("0.40")) == mm("0.40")
    assert unsharpness_budget(Length.ZERO, Length.ZERO) == Length.ZERO


def test_the_larger_term_dominates_the_budget():
    total = unsharpness_budget(mm("0.40"), mm("0.10"))
    assert mm("0.40") < total < mm("0.42")


def test_a_negative_unsharpness_term_is_refused():
    with pytest.raises(ValidationError):
        unsharpness_budget(Length(-1), mm("0.40"))
    with pytest.raises(ValidationError):
        unsharpness_budget(mm("0.40"), Length(-1))
