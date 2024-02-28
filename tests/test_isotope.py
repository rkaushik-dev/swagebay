from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError
from qu_imager.materials import Quality
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import mm
from qu_imager.sources.isotope import (
    CO60,
    IR192,
    SE75,
    isotope,
    isotopes_covering,
    known_isotopes,
)


def test_iridium_has_the_half_life_the_source_certificate_quotes():
    assert IR192.half_life_days == Decimal("73.83")
    assert IR192.half_life == Duration.from_hours(Decimal("1771.92"))


def test_cobalt_lives_years_and_ytterbium_lives_a_month():
    assert CO60.half_life_days > 1900
    assert isotope("yb169").half_life_days == Decimal("32.0")
    assert CO60.half_life > IR192.half_life


def test_the_gamma_constant_is_the_number_the_safety_side_needs():
    assert IR192.gamma_constant == Decimal("0.48")
    assert CO60.gamma_constant > IR192.gamma_constant
    assert SE75.gamma_constant < IR192.gamma_constant


def test_the_practical_range_is_narrower_than_the_physics():
    assert IR192.covers(mm(20))
    assert IR192.covers(mm(12))
    assert IR192.covers(mm(63))
    assert not IR192.covers(mm(8))
    assert not IR192.covers(mm(80))


def test_selenium_takes_the_thin_wall_iridium_washes_out():
    assert SE75.covers(mm(8))
    assert not IR192.covers(mm(8))
    assert SE75.steel_max < IR192.steel_max


def test_cobalt_takes_the_vessel_wall_nothing_else_will():
    assert CO60.covers(mm(120))
    assert not IR192.covers(mm(120))
    assert CO60.steel_min == mm(50)


def test_asking_which_isotopes_cover_a_wall_returns_them_softest_first():
    fits = isotopes_covering(mm(26))
    assert [i.key for i in fits] == ["se75", "ir192", "cs137"]
    assert [i.key for i in isotopes_covering(mm(20))] == ["se75", "ir192"]
    assert isotopes_covering(mm(150)) == (CO60,)
    assert isotopes_covering(mm(300)) == ()


def test_the_e94_hole_is_reported_and_not_papered_over():
    assert IR192.quality_for_equivalence() is Quality.IR192
    assert CO60.quality_for_equivalence() is Quality.CO60
    with pytest.raises(ConfigurationError) as caught:
        SE75.quality_for_equivalence()
    assert caught.value.context["isotope"] == "se75"
    with pytest.raises(ConfigurationError):
        isotope("yb169").quality_for_equivalence()


def test_cobalt_is_two_lines_and_iridium_is_a_spread():
    assert IR192.is_broad_spectrum
    assert SE75.is_broad_spectrum
    assert not CO60.is_broad_spectrum
    assert not isotope("cs137").is_broad_spectrum


def test_caesium_is_a_single_line_at_six_six_two():
    cs = isotope("cs137")
    assert cs.energy_kev_low == cs.energy_kev_high == 662


def test_the_catalogue_is_closed_and_sorted():
    keys = known_isotopes()
    assert keys == ("co60", "cs137", "ir192", "se75", "yb169")
    with pytest.raises(ConfigurationError):
        isotope("ra226")


def test_an_isotope_prints_as_the_symbol_on_the_pig():
    assert str(IR192) == "Ir-192"
    assert str(CO60) == "Co-60"
    assert IR192.name == "iridium 192"
