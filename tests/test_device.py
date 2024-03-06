from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.activity import curie
from qu_imager.quantities.dose import usv_per_hour
from qu_imager.quantities.length import Length
from qu_imager.sources.device import (
    Collimator,
    collimator,
    device_model,
    devices_for,
    known_collimators,
    known_devices,
)
from qu_imager.sources.isotope import CO60, IR192, SE75


def test_a_device_is_certified_for_one_isotope_and_one_capacity():
    delta = device_model("880d")
    assert delta.isotope is IR192
    assert delta.capacity == curie(130)
    assert delta.accepts(IR192, curie(100))
    assert not delta.accepts(IR192, curie(140))
    assert not delta.accepts(CO60, curie(10))


def test_loading_the_wrong_isotope_is_refused_by_name():
    delta = device_model("880d")
    with pytest.raises(ConfigurationError) as caught:
        delta.check_load(SE75, curie(50))
    assert caught.value.context["certified_for"] == "ir192"
    assert caught.value.context["offered"] == "se75"


def test_overloading_a_device_is_refused_with_both_numbers():
    delta = device_model("880d")
    with pytest.raises(ConfigurationError) as caught:
        delta.check_load(IR192, curie(150))
    assert caught.value.context["capacity_ci"] == "130"
    assert caught.value.context["offered_ci"] == "150"
    delta.check_load(IR192, curie(130))


def test_the_certificate_rates_scale_with_what_is_actually_in_the_box():
    delta = device_model("880d")
    assert delta.metre_rate_at_capacity == usv_per_hour(18)
    assert delta.metre_rate_with(curie(65)) == usv_per_hour(9)
    assert delta.surface_rate_with(curie(130)) == delta.surface_rate_at_capacity


def test_an_empty_device_reads_nothing():
    delta = device_model("880d")
    assert delta.metre_rate_with(curie(0)).nsv_per_hour == 0


def test_devices_for_an_isotope_come_back_biggest_first():
    ir = devices_for(IR192)
    assert [d.key for d in ir] == ["880s", "880d", "tsi3"]
    assert [d.key for d in devices_for(SE75)] == ["spec150"]
    assert [d.key for d in devices_for(CO60)] == ["tk100"]


def test_the_cobalt_device_is_the_one_nobody_wants_to_carry():
    assert device_model("tk100").mass_kg > device_model("880d").mass_kg * 5
    assert device_model("spec150").mass_kg < device_model("880d").mass_kg


def test_guide_tube_length_is_part_of_the_certificate():
    assert device_model("880d").max_guide_tube == Length.from_m(12)
    assert device_model("tk100").max_guide_tube == Length.from_m(6)


def test_a_collimator_opens_a_sector_and_shields_the_rest():
    cup = collimator("w60")
    assert cup.covers(Decimal(0))
    assert cup.covers(Decimal(30))
    assert not cup.covers(Decimal(31))
    assert cup.factor_at(Decimal(0)) == Decimal(1)
    assert cup.factor_at(Decimal(90)) == Decimal("0.06")


def test_bearings_wrap_the_way_a_compass_does():
    cup = collimator("w90")
    assert cup.covers(Decimal(350))
    assert cup.covers(Decimal(-40))
    assert not cup.covers(Decimal(180))
    assert cup.factor_at(Decimal(359)) == Decimal(1)


def test_no_collimator_is_an_entry_in_the_table_not_a_none():
    bare = collimator("none")
    assert bare.aperture_degrees == 360
    assert bare.factor_at(Decimal(180)) == Decimal(1)
    assert bare.mass_kg == 0


def test_a_nonsense_collimator_is_refused_at_construction():
    with pytest.raises(ValidationError):
        Collimator("bad", "bad", "tungsten", 0, Decimal("0.1"), Decimal(1))
    with pytest.raises(ValidationError):
        Collimator("bad", "bad", "tungsten", 60, Decimal(0), Decimal(1))
    with pytest.raises(ValidationError):
        Collimator("bad", "bad", "tungsten", 60, Decimal("1.5"), Decimal(1))


def test_both_catalogues_are_closed():
    assert known_devices() == ("880d", "880s", "spec150", "tk100", "tsi3")
    assert known_collimators() == ("du45", "none", "w60", "w90")
    with pytest.raises(ConfigurationError):
        device_model("880z")
    with pytest.raises(ConfigurationError):
        collimator("lead pipe")
