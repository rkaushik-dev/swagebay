from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.film import (
    DENSITY_CEILING,
    REFERENCE_FILM,
    FilmClass,
    FilmSystem,
    density_floor,
    density_in_range,
    film,
    films_in_class,
    known_films,
    variation_acceptable,
    variation_window,
)


def test_the_chart_film_is_the_reference_and_needs_no_scaling():
    assert REFERENCE_FILM.key == "d7"
    assert REFERENCE_FILM.time_factor_from(REFERENCE_FILM) == Decimal(1)


def test_a_faster_film_needs_less_time():
    fast = film("d8")
    assert fast.time_factor_from(REFERENCE_FILM) < 1
    assert fast.speed_index > REFERENCE_FILM.speed_index


def test_a_slower_film_needs_more_time_and_the_factor_is_the_speed_ratio():
    slow = film("d4")
    assert slow.time_factor_from(REFERENCE_FILM) == Decimal(100) / Decimal(32)
    assert slow.time_factor_from(REFERENCE_FILM) > 3


def test_speed_is_bought_with_grain():
    assert film("d2").is_finer_than(film("d8"))
    assert film("mx125").is_finer_than(film("aa400"))
    assert not film("hs800").is_finer_than(film("d2"))


def test_the_classes_sort_the_way_the_standard_lists_them():
    assert film("d2").film_class is FilmClass.SPECIAL
    assert film("d4").film_class is FilmClass.CLASS_I
    assert film("d7").film_class is FilmClass.CLASS_II
    assert film("hs800").film_class is FilmClass.CLASS_III
    assert str(FilmClass.CLASS_II) == "II"


def test_films_in_a_class_come_back_slowest_first():
    class_one = films_in_class(FilmClass.CLASS_I)
    assert [f.key for f in class_one] == ["d3", "mx125", "d4", "t200"]
    assert films_in_class(FilmClass.SPECIAL) == (film("d2"),)


def test_a_film_with_no_speed_is_refused():
    with pytest.raises(ValidationError):
        FilmSystem("x", "x", "x", FilmClass.CLASS_I, Decimal(0), Decimal("0.02"))


def test_gamma_needs_more_density_than_x_rays_do():
    assert density_floor(gamma=True) == Decimal("2.0")
    assert density_floor(gamma=False) == Decimal("1.8")
    assert density_floor(gamma=True, composite=True) == Decimal("1.3")
    assert density_floor(gamma=False, composite=True) == Decimal("1.3")


def test_the_density_gate_is_hard_at_both_ends():
    assert density_in_range(Decimal("2.0"), gamma=True)
    assert not density_in_range(Decimal("1.99"), gamma=True)
    assert density_in_range(Decimal("1.9"), gamma=False)
    assert density_in_range(DENSITY_CEILING, gamma=True)
    assert not density_in_range(Decimal("4.01"), gamma=True)


def test_a_thin_film_that_would_pass_on_x_rays_fails_on_iridium():
    reading = Decimal("1.85")
    assert density_in_range(reading, gamma=False)
    assert not density_in_range(reading, gamma=True)


def test_the_variation_window_is_not_symmetric():
    low, high = variation_window(Decimal("2.5"))
    assert low == Decimal("2.125")
    assert high == Decimal("3.25")
    assert high - Decimal("2.5") > Decimal("2.5") - low


def test_variation_is_read_against_the_density_through_the_indicator():
    through_iqi = Decimal("2.5")
    assert variation_acceptable(through_iqi, Decimal("2.5"))
    assert variation_acceptable(through_iqi, Decimal("2.13"))
    assert not variation_acceptable(through_iqi, Decimal("2.0"))
    assert variation_acceptable(through_iqi, Decimal("3.25"))
    assert not variation_acceptable(through_iqi, Decimal("3.3"))


def test_a_zero_indicator_reading_is_refused_rather_than_dividing_by_it():
    with pytest.raises(ValidationError):
        variation_window(Decimal(0))
    with pytest.raises(ValidationError):
        variation_acceptable(Decimal(0), Decimal(2))


def test_the_catalogue_is_closed():
    keys = known_films()
    assert len(keys) == 10
    assert "d7" in keys
    with pytest.raises(ConfigurationError):
        film("d9")
