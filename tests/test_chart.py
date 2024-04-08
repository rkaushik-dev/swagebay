from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.exposure.chart import ChartPoint, ExposureChart, chart_from_inches
from qu_imager.quantities.length import Length, inch, mm

READINGS = (
    ("0.25", "4"),
    ("0.50", "8"),
    ("0.75", "16"),
    ("1.00", "30"),
    ("1.25", "55"),
    ("1.50", "100"),
    ("1.75", "180"),
    ("2.00", "320"),
)


def chart():
    return chart_from_inches("ir192-d7-2.0", "ir192", "carbon_steel", "d7", "2.0", "24", READINGS)


def test_a_point_on_the_chart_reads_back_exactly():
    assert chart().factor_for(inch("1.00")) == Decimal(30)
    assert chart().factor_for(inch("0.25")) == Decimal(4)
    assert chart().factor_for(inch("2.00")) == Decimal(320)


def test_the_chart_knows_what_it_was_drawn_for():
    card = chart()
    assert card.isotope_key == "ir192"
    assert card.film_key == "d7"
    assert card.density == Decimal("2.0")
    assert card.reference_sfd == inch(24)


def test_interpolation_is_logarithmic_not_linear():
    midpoint = chart().factor_for(inch("1.125"))
    linear = (Decimal(30) + Decimal(55)) / 2
    geometric = (Decimal(30) * Decimal(55)).sqrt()
    assert abs(midpoint - geometric) < Decimal("0.01")
    assert midpoint < linear


def test_linear_reading_would_come_out_long_in_every_interval():
    card = chart()
    for low, high in zip(READINGS, READINGS[1:], strict=False):
        middle = (Length.from_inch(low[0]) + Length.from_inch(high[0])) / 2
        linear = (Decimal(low[1]) + Decimal(high[1])) / 2
        assert card.factor_for(middle) < linear


def test_the_curve_rises_all_the_way_along():
    card = chart()
    values = [card.factor_for(mm(t)) for t in range(7, 50, 3)]
    assert values == sorted(values)


def test_a_thickness_off_the_end_is_refused_and_names_the_range():
    card = chart()
    with pytest.raises(TechniqueError) as caught:
        card.factor_for(inch("2.5"))
    assert caught.value.context["from_mm"] == "6.35"
    assert caught.value.context["to_mm"] == "50.8"
    with pytest.raises(TechniqueError):
        card.factor_for(inch("0.1"))


def test_the_chart_says_what_it_covers():
    card = chart()
    assert card.covers(inch("1.0"))
    assert card.covers(card.thinnest)
    assert card.covers(card.thickest)
    assert not card.covers(inch("2.01"))


def test_reading_the_chart_backwards_finds_the_thickness():
    card = chart()
    assert card.thickness_for(Decimal(30)) == inch("1.00")
    recovered = card.thickness_for(card.factor_for(inch("1.30")))
    assert abs(recovered.um - inch("1.30").um) < 20


def test_an_exposure_off_the_end_is_refused_backwards_too():
    card = chart()
    with pytest.raises(TechniqueError):
        card.thickness_for(Decimal(1000))
    with pytest.raises(TechniqueError):
        card.thickness_for(Decimal(1))
    with pytest.raises(ValidationError):
        card.thickness_for(Decimal(0))


def test_a_chart_needs_two_points_to_be_a_chart():
    with pytest.raises(ConfigurationError):
        ExposureChart(
            key="thin",
            isotope_key="ir192",
            material_key="carbon_steel",
            film_key="d7",
            density=Decimal(2),
            reference_sfd=inch(24),
            points=(ChartPoint(mm(10), Decimal(5)),),
        )


def test_a_chart_out_of_order_is_refused():
    points = (
        ChartPoint(mm(20), Decimal(10)),
        ChartPoint(mm(10), Decimal(5)),
    )
    with pytest.raises(ConfigurationError) as caught:
        ExposureChart("bad", "ir192", "carbon_steel", "d7", Decimal(2), inch(24), points)
    assert "out of order" in caught.value.message


def test_a_chart_that_falls_is_refused():
    points = (
        ChartPoint(mm(10), Decimal(50)),
        ChartPoint(mm(20), Decimal(10)),
    )
    with pytest.raises(ConfigurationError) as caught:
        ExposureChart("bad", "ir192", "carbon_steel", "d7", Decimal(2), inch(24), points)
    assert "rise" in caught.value.message


def test_two_points_at_one_thickness_are_refused():
    points = (
        ChartPoint(mm(10), Decimal(5)),
        ChartPoint(mm(10), Decimal(10)),
    )
    with pytest.raises(ConfigurationError):
        ExposureChart("bad", "ir192", "carbon_steel", "d7", Decimal(2), inch(24), points)


def test_a_point_needs_a_real_thickness_and_a_real_exposure():
    with pytest.raises(ValidationError):
        ChartPoint(Length.ZERO, Decimal(5))
    with pytest.raises(ValidationError):
        ChartPoint(mm(10), Decimal(0))
