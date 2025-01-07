from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.reporting.statistics import (
    MINIMUM_DENOMINATOR,
    Band,
    Period,
    Rate,
    as_percentage,
    band_from,
    by_welder,
    combine,
    excursions,
    reportable_welders,
    rising,
)


def period(month, repaired, shot, year=2024):
    return Period(year=year, month=month, rate=Rate(repaired, shot))


def test_a_rate_carries_the_count_it_came_from():
    rate = Rate(4, 100)
    assert rate.value == Decimal("0.04")
    assert rate.describe() == "4.00% on 100"


def test_a_rate_on_too_few_welds_is_not_reported_as_a_rate():
    small = Rate(1, 19)
    assert not small.is_reportable
    assert small.describe() == "1 of 19, too few to rate"
    assert Rate(1, MINIMUM_DENOMINATOR).is_reportable


def test_the_same_percentage_reads_differently_at_two_denominators():
    big = Rate(40, 1000)
    small = Rate(1, 25)
    assert big.value == small.value
    assert "on 1000" in big.describe()
    assert "on 25" in small.describe()


def test_more_repairs_than_welds_is_refused():
    with pytest.raises(ValidationError):
        Rate(5, 4)
    with pytest.raises(ValidationError):
        Rate(-1, 4)


def test_a_shop_with_no_welds_has_a_rate_of_nothing_rather_than_a_crash():
    assert Rate(0, 0).value == Decimal(0)
    assert not Rate(0, 0).is_reportable


def test_pooling_is_not_the_average_of_the_rates():
    pooled = combine([Rate(1, 100), Rate(1, 4)])
    assert pooled.repaired == 2
    assert pooled.shot == 104
    average = (Decimal("0.01") + Decimal("0.25")) / 2
    assert pooled.value < average


def test_a_month_outside_the_calendar_is_refused():
    with pytest.raises(ValidationError):
        Period(2024, 13, Rate(1, 100))
    assert period(3, 1, 100).label() == "2024-03"


def test_the_band_comes_from_the_history_and_not_from_a_target():
    history = [period(m, 4, 100) for m in range(1, 7)]
    band = band_from(history)
    assert band.centre == Decimal("0.04")
    assert band.low < band.centre < band.high


def test_a_wider_spread_makes_a_wider_band():
    history = [period(m, 4, 100) for m in range(1, 7)]
    tight = band_from(history, spread=Decimal("1.2"))
    loose = band_from(history, spread=Decimal(2))
    assert loose.high > tight.high
    assert loose.low < tight.low


def test_a_spread_of_one_or_less_is_refused():
    history = [period(m, 4, 100) for m in range(1, 7)]
    with pytest.raises(ValidationError):
        band_from(history, spread=Decimal(1))


def test_a_band_cannot_be_set_from_months_that_are_too_small_to_rate():
    with pytest.raises(ValidationError):
        band_from([period(m, 0, 5) for m in range(1, 7)])


def test_the_months_outside_the_band_are_the_ones_worth_reading():
    history = [period(m, 4, 100) for m in range(1, 7)]
    band = band_from(history)
    bad = period(7, 12, 100)
    found = excursions([*history, bad], band)
    assert [p.label() for p in found] == ["2024-07"]


def test_a_month_too_small_to_rate_is_never_an_excursion():
    band = Band(Decimal("0.04"), Decimal("0.02"), Decimal("0.06"))
    assert excursions([period(7, 1, 5)], band) == ()


def test_a_quiet_month_below_the_band_is_an_excursion_too():
    band = Band(Decimal("0.04"), Decimal("0.02"), Decimal("0.06"))
    assert len(excursions([period(7, 0, 100)], band)) == 1


def test_three_months_each_worse_than_the_last_is_a_rise():
    history = [period(1, 2, 100), period(2, 4, 100), period(3, 6, 100)]
    assert rising(history)


def test_a_month_that_improves_breaks_the_run():
    history = [period(1, 2, 100), period(2, 4, 100), period(3, 3, 100)]
    assert not rising(history)


def test_a_short_history_cannot_be_called_a_rise():
    assert not rising([period(1, 2, 100), period(2, 4, 100)])
    assert rising([period(1, 2, 100), period(2, 4, 100)], run=2)


def test_a_run_of_one_is_refused():
    with pytest.raises(ValidationError):
        rising([period(1, 2, 100)], run=1)


def test_months_too_small_to_rate_are_left_out_of_the_run():
    history = [period(1, 2, 100), period(2, 1, 3), period(3, 4, 100), period(4, 6, 100)]
    assert rising(history)


def test_per_welder_reports_the_rate_and_the_count_without_ranking():
    counts = {"w-19": (4, 100), "w-04": (1, 3)}
    rates = by_welder(counts)
    assert list(rates) == ["w-04", "w-19"]
    assert not rates["w-04"].is_reportable
    assert rates["w-19"].describe() == "4.00% on 100"


def test_only_the_welders_with_enough_welds_are_reportable():
    counts = {"w-19": (4, 100), "w-04": (1, 3)}
    assert reportable_welders(counts) == ("w-19",)


def test_the_percentage_is_rounded_the_way_a_report_rounds():
    assert as_percentage(Rate(1, 3)) == Decimal("33.33")
    assert as_percentage(Rate(1, 8), places=1) == Decimal("12.5")


def test_a_band_prints_the_way_the_monthly_note_reads():
    band = Band(Decimal("0.04"), Decimal("0.02"), Decimal("0.06"))
    assert band.describe() == "4.00% band 2.00% to 6.00%"
