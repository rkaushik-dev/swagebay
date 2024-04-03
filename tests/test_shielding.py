from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.dose import DoseRate, mr_per_hour
from qu_imager.quantities.length import Length, mm
from qu_imager.safety.shielding import (
    SHIELD_MATERIALS,
    Barrier,
    attenuated,
    half_value_layer,
    materials_for,
    tenth_value_layer,
    thickness_for_rate,
    thickness_for_transmission,
    transmission,
)
from qu_imager.sources.isotope import CO60, IR192, SE75


def test_one_half_value_layer_halves_the_rate():
    hvl = half_value_layer(IR192, "lead")
    assert hvl == mm("4.8")
    left = transmission(IR192, "lead", hvl)
    assert abs(left - Decimal("0.5")) < Decimal("1e-20")


def test_ten_layers_cut_it_by_about_a_thousand():
    hvl = half_value_layer(IR192, "lead")
    left = transmission(IR192, "lead", Length(hvl.um * 10))
    assert Decimal("0.0009") < left < Decimal("0.001")


def test_no_shield_lets_everything_through():
    assert transmission(IR192, "lead", Length.ZERO) == Decimal(1)
    assert attenuated(IR192, "lead", Length.ZERO, mr_per_hour(200)) == mr_per_hour(200)


def test_cobalt_needs_more_than_twice_the_lead_iridium_does():
    assert half_value_layer(CO60, "lead") > half_value_layer(IR192, "lead") * 2
    assert half_value_layer(SE75, "lead") < half_value_layer(IR192, "lead")


def test_a_barrier_sized_for_one_isotope_underperforms_for_another():
    barrier = Barrier("lead", half_value_layer(IR192, "lead"))
    assert abs(barrier.transmission(IR192) - Decimal("0.5")) < Decimal("1e-20")
    assert barrier.transmission(CO60) > Decimal("0.75")


def test_a_tenth_value_layer_is_about_three_and_a_third_half_layers():
    tvl = tenth_value_layer(IR192, "lead")
    assert Decimal("3.3") < tvl.ratio(half_value_layer(IR192, "lead")) < Decimal("3.4")
    # the layer is stored to the micrometre, so the tenth is a tenth to five places
    assert abs(transmission(IR192, "lead", tvl) - Decimal("0.1")) < Decimal("1e-4")


def test_the_layers_a_barrier_represents():
    barrier = Barrier("lead", mm("9.6"))
    assert barrier.layers(IR192) == Decimal(2)
    assert abs(barrier.transmission(IR192) - Decimal("0.25")) < Decimal("1e-20")


def test_attenuating_a_real_reading():
    shielded = attenuated(IR192, "lead", mm("4.8"), mr_per_hour(200))
    assert abs(shielded.mr_per_hour - Decimal(100)) < Decimal("0.01")


def test_the_thickness_needed_for_a_fraction_inverts_the_transmission():
    lead = thickness_for_transmission(IR192, "lead", Decimal("0.25"))
    assert abs(lead.um - mm("9.6").um) <= 1
    assert thickness_for_transmission(IR192, "lead", Decimal(1)) == Length.ZERO


def test_a_fraction_outside_the_unit_interval_is_refused():
    for bad in [Decimal(0), Decimal("-0.5"), Decimal(2)]:
        with pytest.raises(ValidationError):
            thickness_for_transmission(IR192, "lead", bad)


def test_the_barrier_that_brings_a_rate_down_to_a_target():
    needed = thickness_for_rate(IR192, "lead", mr_per_hour(200), mr_per_hour(2))
    assert attenuated(IR192, "lead", needed, mr_per_hour(200)) <= mr_per_hour(2)
    assert needed > mm(30)


def test_no_barrier_is_needed_when_the_rate_is_already_under():
    assert thickness_for_rate(IR192, "lead", mr_per_hour(1), mr_per_hour(2)) == Length.ZERO
    assert thickness_for_rate(IR192, "lead", mr_per_hour(2), mr_per_hour(2)) == Length.ZERO


def test_a_target_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        thickness_for_rate(IR192, "lead", mr_per_hour(200), DoseRate.ZERO)


def test_concrete_takes_an_order_of_magnitude_more_than_lead():
    assert half_value_layer(IR192, "concrete") > half_value_layer(IR192, "lead") * 9
    assert half_value_layer(IR192, "water") > half_value_layer(IR192, "concrete") * 3


def test_an_unlisted_material_is_refused_rather_than_estimated():
    with pytest.raises(ConfigurationError) as caught:
        half_value_layer(IR192, "gypsum")
    assert caught.value.context["material"] == "gypsum"
    assert materials_for(IR192) == SHIELD_MATERIALS


def test_a_negative_barrier_is_refused():
    with pytest.raises(ValidationError):
        Barrier("lead", Length(-1))
    with pytest.raises(ValidationError):
        transmission(IR192, "lead", Length(-1))


def test_a_barrier_prints_the_way_a_survey_note_reads():
    assert str(Barrier("lead", mm("4.8"))) == "4.80 mm of lead"
