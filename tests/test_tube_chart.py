from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.exposure.tube_chart import Curve, TubeChart, curve_from_mm, split
from qu_imager.quantities.duration import seconds
from qu_imager.quantities.length import Length, mm
from qu_imager.sources.xray import tube

READINGS_150 = (("6", "12"), ("10", "24"), ("13", "40"), ("19", "95"))
READINGS_180 = (("10", "12"), ("13", "18"), ("19", "40"), ("25", "88"))
READINGS_200 = (("13", "10"), ("19", "20"), ("25", "40"), ("32", "88"))


def a_chart():
    return TubeChart(
        key="steel-d7-2.0-700",
        material_key="carbon_steel",
        film_key="d7",
        density=Decimal("2.0"),
        reference_sfd=mm(700),
        curves=(
            curve_from_mm(150, READINGS_150),
            curve_from_mm(180, READINGS_180),
            curve_from_mm(200, READINGS_200),
        ),
    )


def test_a_point_on_a_curve_reads_back_exactly():
    assert a_chart().mas_for(150, mm(13)) == Decimal(40)
    assert a_chart().mas_for(200, mm(25)) == Decimal(40)


def test_a_harder_beam_needs_less_exposure_at_the_same_thickness():
    chart = a_chart()
    assert chart.mas_for(200, mm(19)) < chart.mas_for(180, mm(19)) < chart.mas_for(150, mm(19))


def test_interpolation_along_a_curve_is_logarithmic():
    chart = a_chart()
    middle = chart.mas_for(150, mm(8))
    geometric = (Decimal(12) * Decimal(24)).sqrt()
    assert abs(middle - geometric) < Decimal("0.01")


def test_a_thickness_off_the_end_of_a_curve_is_refused():
    with pytest.raises(TechniqueError) as caught:
        a_chart().mas_for(150, mm(30))
    assert caught.value.context["to_mm"] == "19"


def test_a_kilovoltage_with_no_curve_is_refused_and_names_the_ones_there_are():
    with pytest.raises(TechniqueError) as caught:
        a_chart().mas_for(165, mm(13))
    assert caught.value.context["established"] == "150, 180, 200"


def test_the_nearest_established_curve_can_be_asked_for_explicitly():
    chart = a_chart()
    assert chart.nearest_kilovoltage(155) == 150
    assert chart.nearest_kilovoltage(175) == 180
    assert chart.nearest_kilovoltage(300) == 200


def test_a_tie_goes_to_the_harder_beam():
    assert a_chart().nearest_kilovoltage(165) == 180


def test_the_chart_lists_its_kilovoltages_in_order():
    assert a_chart().kilovoltages() == (150, 180, 200)


def test_a_chart_with_no_curves_or_a_repeated_one_is_refused():
    with pytest.raises(ConfigurationError):
        TubeChart("x", "carbon_steel", "d7", Decimal(2), mm(700), ())
    with pytest.raises(ConfigurationError):
        TubeChart(
            "x",
            "carbon_steel",
            "d7",
            Decimal(2),
            mm(700),
            (curve_from_mm(150, READINGS_150), curve_from_mm(150, READINGS_150)),
        )


def test_a_curve_that_is_out_of_order_or_falls_is_refused():
    with pytest.raises(ConfigurationError):
        Curve(150, ((mm(19), Decimal(95)), (mm(6), Decimal(12))))
    with pytest.raises(ConfigurationError):
        Curve(150, ((mm(6), Decimal(95)), (mm(19), Decimal(12))))
    with pytest.raises(ConfigurationError):
        Curve(150, ((mm(6), Decimal(12)),))


def test_an_exposure_splits_into_a_current_and_a_time():
    current, span = split(Decimal(40), tube("eresco42"))
    assert current == Decimal(10)
    assert span == seconds(4)


def test_a_lower_current_gives_a_longer_time():
    _current, fast = split(Decimal(40), tube("eresco42"), preferred_ma=Decimal(10))
    _current, slow = split(Decimal(40), tube("eresco42"), preferred_ma=Decimal(5))
    assert slow > fast


def test_a_current_over_the_set_is_refused():
    with pytest.raises(TechniqueError):
        split(Decimal(40), tube("eresco42"), preferred_ma=Decimal(20))


def test_an_exposure_under_a_second_is_refused_rather_than_printed():
    with pytest.raises(TechniqueError) as caught:
        split(Decimal("0.5"), tube("eresco42"))
    assert caught.value.context["ma"] == "10"


def test_an_exposure_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        split(Decimal(0), tube("eresco42"))


def test_a_curve_says_what_it_covers():
    curve = curve_from_mm(150, READINGS_150)
    assert curve.thinnest == mm(6)
    assert curve.thickest == mm(19)
    assert curve.covers(mm(10))
    assert not curve.covers(Length(1))
