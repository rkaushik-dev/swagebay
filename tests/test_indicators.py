from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.indicators import (
    HoleIndicator,
    HoleSize,
    WireIndicator,
    hole_identities,
    next_thicker_identity,
    wire_numbers,
    wire_set,
    wire_sets_containing,
)
from qu_imager.quantities.length import Length, inch, mm, thou


def test_an_identity_number_is_thousandths_of_an_inch():
    assert HoleIndicator(30).thickness == thou(30)
    assert HoleIndicator(30).thickness == inch("0.030")
    assert HoleIndicator(5).thickness == thou(5)


def test_the_holes_are_multiples_of_the_plaque_thickness():
    plaque = HoleIndicator(30)
    assert plaque.hole_diameter(HoleSize.ONE_T) == thou(30)
    assert plaque.hole_diameter(HoleSize.TWO_T) == thou(60)
    assert plaque.hole_diameter(HoleSize.FOUR_T) == thou(120)


def test_a_thin_plaque_hits_the_drilling_floor():
    thin = HoleIndicator(5)
    assert thin.hole_diameter(HoleSize.ONE_T) == thou(10)
    assert thin.hole_diameter(HoleSize.TWO_T) == thou(10)
    assert thin.hole_diameter(HoleSize.FOUR_T) == thou(20)


def test_thickness_sensitivity_is_the_plain_percentage():
    plaque = HoleIndicator(20)
    assert plaque.thickness_sensitivity_at(inch(1)) == Decimal(2)
    assert plaque.thickness_sensitivity_at(inch("0.5")) == Decimal(4)


def test_equivalent_sensitivity_reads_the_hole_as_well_as_the_plaque():
    plaque = HoleIndicator(20)
    two_t = plaque.equivalent_sensitivity_at(inch(1), HoleSize.TWO_T)
    four_t = plaque.equivalent_sensitivity_at(inch(1), HoleSize.FOUR_T)
    assert four_t > two_t
    assert Decimal("1.9") < two_t < Decimal("2.1")


def test_a_thin_plaque_with_a_big_hole_can_match_a_thick_one_with_a_small_hole():
    thin = HoleIndicator(20).equivalent_sensitivity_at(inch(1), HoleSize.FOUR_T)
    thick = HoleIndicator(40).equivalent_sensitivity_at(inch(1), HoleSize.ONE_T)
    assert abs(thin - thick) < Decimal("0.6")


def test_sensitivity_needs_something_to_have_been_penetrated():
    with pytest.raises(ValidationError):
        HoleIndicator(20).thickness_sensitivity_at(Length.ZERO)
    with pytest.raises(ValidationError):
        HoleIndicator(20).equivalent_sensitivity_at(Length.ZERO, HoleSize.TWO_T)
    with pytest.raises(ValidationError):
        WireIndicator(10).sensitivity_at(Length.ZERO)


def test_the_hole_labels_are_what_gets_written_on_the_report():
    assert str(HoleSize.TWO_T) == "2T"
    assert HoleSize.FOUR_T.label == "4T"
    assert str(HoleIndicator(30)) == "hole 30"
    assert HoleIndicator(30).label() == "30"


def test_an_unpublished_identity_is_refused():
    with pytest.raises(ConfigurationError):
        HoleIndicator(11)
    with pytest.raises(ConfigurationError):
        HoleIndicator(0)
    assert 12 in hole_identities()


def test_the_next_plaque_up_is_the_next_published_one_not_the_next_integer():
    assert next_thicker_identity(50) == 60
    assert next_thicker_identity(160) == 200
    with pytest.raises(ConfigurationError):
        next_thicker_identity(280)


def test_wire_diameters_are_the_iso_series():
    assert WireIndicator(1).diameter == mm("3.20")
    assert WireIndicator(10).diameter == mm("0.40")
    assert WireIndicator(19).diameter == mm("0.05")
    assert str(WireIndicator(13)) == "W13"


def test_a_higher_wire_number_is_a_thinner_wire():
    diameters = [WireIndicator(n).diameter for n in wire_numbers()]
    assert diameters == sorted(diameters, reverse=True)


def test_wire_sensitivity_is_the_diameter_over_what_was_penetrated():
    assert WireIndicator(6).sensitivity_at(mm(50)) == Decimal(2)
    assert WireIndicator(9).sensitivity_at(mm(25)) == Decimal(2)


def test_the_sets_overlap_so_a_crew_can_carry_two_and_cover_the_range():
    assert [w.number for w in wire_set("A")] == [1, 2, 3, 4, 5, 6, 7]
    assert [w.number for w in wire_set("b")] == [6, 7, 8, 9, 10, 11, 12]
    assert wire_sets_containing(6) == ("A", "B")
    assert wire_sets_containing(13) == ("C", "D")
    assert wire_sets_containing(1) == ("A",)


def test_an_unknown_wire_or_set_is_refused():
    with pytest.raises(ConfigurationError):
        WireIndicator(20)
    with pytest.raises(ConfigurationError):
        wire_set("E")
