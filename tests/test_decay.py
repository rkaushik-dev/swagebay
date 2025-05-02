from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.duration import Duration
from qu_imager.sources.decay import (
    activity_on,
    date_reaching,
    decay,
    decay_fraction,
    elapsed_for_fraction,
    half_lives_elapsed,
    time_to_reach,
)
from qu_imager.sources.isotope import CO60, IR192, SE75

ASSAY = datetime(2024, 3, 1, 12, 0, tzinfo=UTC)


def test_one_half_life_leaves_exactly_half():
    left = decay_fraction(IR192, IR192.half_life)
    assert abs(left - Decimal("0.5")) < Decimal("1e-20")
    assert decay(curie(90), IR192, IR192.half_life).curie == Decimal(45)


def test_two_half_lives_leave_a_quarter():
    two = Duration(IR192.half_life.ms * 2)
    assert decay(curie(80), IR192, two).curie == Decimal(20)


def test_nothing_decays_in_no_time():
    assert decay_fraction(IR192, Duration.ZERO) == Decimal(1)
    assert decay(curie(90), IR192, Duration.ZERO) == curie(90)


def test_iridium_loses_a_third_of_itself_in_five_weeks():
    left = decay(curie(90), IR192, Duration.from_hours(35 * 24))
    assert Decimal("0.71") < left.ratio(curie(90)) < Decimal("0.73")


def test_cobalt_hardly_moves_over_the_same_five_weeks():
    left = decay(curie(90), CO60, Duration.from_hours(35 * 24))
    assert left.ratio(curie(90)) > Decimal("0.98")


def test_a_certificate_decays_to_the_morning_of_the_shot():
    shot = ASSAY + timedelta(days=74)
    on_the_day = activity_on(curie(100), ASSAY, IR192, shot)
    assert Decimal("49") < on_the_day.curie < Decimal("50")


def test_the_assay_instant_returns_the_certificate_value():
    assert activity_on(curie(100), ASSAY, IR192, ASSAY) == curie(100)


def test_a_source_cannot_be_decayed_backwards():
    with pytest.raises(ValidationError):
        activity_on(curie(100), ASSAY, IR192, ASSAY - timedelta(days=1))


def test_an_aware_certificate_and_a_naive_shot_date_are_refused():
    naive = datetime(2024, 5, 1, 12, 0)
    with pytest.raises(ValidationError) as caught:
        activity_on(curie(100), ASSAY, IR192, naive)
    assert caught.value.context["left_aware"] is True
    assert caught.value.context["right_aware"] is False


def test_the_time_to_fall_to_a_fraction_inverts_the_decay():
    span = elapsed_for_fraction(IR192, Decimal("0.5"))
    assert abs(span.ms - IR192.half_life.ms) < 1000
    quarter = elapsed_for_fraction(IR192, Decimal("0.25"))
    assert abs(quarter.ms - IR192.half_life.ms * 2) < 2000


def test_a_fraction_outside_the_unit_interval_is_refused():
    for bad in [Decimal(0), Decimal(-1), Decimal("1.5")]:
        with pytest.raises(ValidationError):
            elapsed_for_fraction(IR192, bad)
    assert elapsed_for_fraction(IR192, Decimal(1)) == Duration.ZERO


def test_the_time_from_one_activity_down_to_another():
    span = time_to_reach(SE75, curie(80), curie(20))
    assert abs(span.ms - SE75.half_life.ms * 2) < 2000


def test_a_source_does_not_grow_and_never_reaches_zero():
    with pytest.raises(ValidationError):
        time_to_reach(IR192, curie(20), curie(80))
    with pytest.raises(ValidationError):
        time_to_reach(IR192, curie(20), Activity.ZERO)
    with pytest.raises(ValidationError):
        time_to_reach(IR192, Activity.ZERO, curie(1))


def test_the_date_a_source_falls_below_the_working_floor():
    retire = date_reaching(IR192, curie(100), curie(25), ASSAY)
    assert retire.date() == (ASSAY + timedelta(days=147.66)).date()
    assert retire.date().isoformat() == "2024-07-27"


def test_half_lives_elapsed_is_what_the_history_sheet_quotes():
    two = Duration(IR192.half_life.ms * 2)
    assert half_lives_elapsed(IR192, two) == Decimal(2)
    assert half_lives_elapsed(IR192, Duration.ZERO) == Decimal(0)
