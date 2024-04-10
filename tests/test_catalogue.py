from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError
from qu_imager.exposure.catalogue import (
    all_charts,
    chart,
    chart_for,
    chart_keys,
    charts_for_film_substitution,
)
from qu_imager.quantities.length import inch


def test_the_shop_owns_six_curves():
    assert len(all_charts()) == 6
    assert len(chart_keys()) == 6
    assert "ir192-steel-d7-2.0" in chart_keys()


def test_every_chart_is_internally_sound():
    for card in all_charts():
        assert card.thinnest < card.thickest
        assert card.factor_for(card.thinnest) < card.factor_for(card.thickest)


def test_the_working_chart_is_the_iridium_steel_one():
    card = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    assert card.key == "ir192-steel-d7-2.0"
    assert card.factor_for(inch("1.00")) == Decimal(30)
    assert card.reference_sfd == inch(24)


def test_a_slower_film_takes_about_twice_the_exposure():
    fast = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    slow = chart_for("ir192", "carbon_steel", "d5", Decimal("2.0"))
    ratio = slow.factor_for(inch(1)) / fast.factor_for(inch(1))
    assert Decimal("1.7") < ratio < Decimal("2.0")


def test_a_higher_density_costs_more_exposure_at_the_same_thickness():
    plain = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    dense = chart_for("ir192", "carbon_steel", "d7", Decimal("2.5"))
    assert dense.factor_for(inch(1)) > plain.factor_for(inch(1))


def test_selenium_is_slower_than_iridium_at_the_same_thickness():
    ir = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    se = chart_for("se75", "carbon_steel", "d7", Decimal("2.0"))
    assert se.factor_for(inch("0.5")) > ir.factor_for(inch("0.5")) * 4


def test_the_charts_cover_the_ranges_their_isotopes_are_used_over():
    se = chart_for("se75", "carbon_steel", "d7", Decimal("2.0"))
    co = chart_for("co60", "carbon_steel", "d7", Decimal("2.0"))
    assert se.thickest < inch("1.01")
    assert co.thinnest >= inch(2)
    assert co.thickest == inch(8)


def test_the_cobalt_chart_is_drawn_at_a_longer_distance():
    co = chart_for("co60", "carbon_steel", "d7", Decimal("2.0"))
    ir = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    assert co.reference_sfd == inch(36)
    assert ir.reference_sfd == inch(24)


def test_aluminium_shoots_far_faster_than_steel():
    alu = chart_for("ir192", "aluminium", "d7", Decimal("2.0"))
    steel = chart_for("ir192", "carbon_steel", "d7", Decimal("2.0"))
    assert alu.factor_for(inch(1)) < steel.factor_for(inch(1)) / 5


def test_an_unestablished_combination_is_refused_and_names_all_four():
    with pytest.raises(ConfigurationError) as caught:
        chart_for("ir192", "inconel", "d7", Decimal("2.0"))
    assert caught.value.context["material"] == "inconel"
    assert caught.value.context["film"] == "d7"
    assert caught.value.context["density"] == "2.0"


def test_a_density_is_never_substituted_silently():
    with pytest.raises(ConfigurationError):
        chart_for("ir192", "carbon_steel", "d7", Decimal("2.2"))


def test_film_substitution_returns_the_charts_that_match_everything_else():
    options = charts_for_film_substitution("ir192", "carbon_steel", Decimal("2.0"))
    assert {c.film_key for c in options} == {"d7", "d5"}
    assert charts_for_film_substitution("co60", "carbon_steel", Decimal("2.5")) == ()


def test_a_chart_can_be_fetched_by_key():
    assert chart("se75-steel-d7-2.0").isotope_key == "se75"
    with pytest.raises(ConfigurationError):
        chart("ir192-steel-d3-2.0")
