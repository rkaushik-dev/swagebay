from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.aggregate import (
    STANDARD_WINDOW,
    aggregate_in,
    clipped_length,
    count_in_worst_window,
    fraction_of_weld,
    window_applies,
    worst_window,
)
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length, inch, mm


def slag(at, length):
    return Indication(Kind.ESI, mm(at), mm(length), mm(1))


def test_the_window_is_twelve_inches():
    assert inch(12) == STANDARD_WINDOW
    assert STANDARD_WINDOW == mm("304.8")


def test_a_weld_with_nothing_in_it_has_an_empty_worst_window():
    found = worst_window((), mm(1000))
    assert found.aggregate == Length.ZERO
    assert found.count == 0


def test_the_same_total_spread_out_and_bunched_up_read_differently():
    spread = tuple(slag(at=i * 130, length=12) for i in range(8))
    bunched = tuple(slag(at=i * 35, length=12) for i in range(8))
    weld = mm(1100)
    assert worst_window(spread, weld).aggregate < worst_window(bunched, weld).aggregate
    assert worst_window(bunched, weld).aggregate == mm(96)


def test_the_window_finds_the_worst_position_not_the_first_one():
    indications = (slag(at=10, length=10), slag(at=700, length=40), slag(at=760, length=40))
    found = worst_window(indications, mm(1200))
    assert found.aggregate == mm(80)
    assert found.count == 2
    assert found.starts_at >= mm(490)


def test_an_indication_straddling_the_edge_is_clipped():
    long_one = slag(at=0, length=400)
    assert clipped_length(long_one, mm(0), mm(100)) == mm(100)
    assert clipped_length(long_one, mm(350), mm(500)) == mm(50)
    assert clipped_length(long_one, mm(500), mm(600)) == Length.ZERO


def test_a_window_never_counts_more_than_its_own_length():
    one_long = (slag(at=0, length=900),)
    found = worst_window(one_long, mm(1000))
    assert found.aggregate == STANDARD_WINDOW


def test_the_aggregate_in_a_named_window_counts_what_contributed():
    indications = (slag(at=100, length=20), slag(at=200, length=30), slag(at=900, length=10))
    total, count = aggregate_in(indications, mm(0), mm(400))
    assert total == mm(50)
    assert count == 2


def test_a_window_with_no_width_is_refused():
    with pytest.raises(ValidationError):
        aggregate_in((), mm(100), mm(100))
    with pytest.raises(ValidationError):
        worst_window((slag(at=0, length=10),), mm(1000), Length.ZERO)
    with pytest.raises(ValidationError):
        worst_window((slag(at=0, length=10),), Length.ZERO)


def test_a_short_weld_uses_its_own_length_as_the_window():
    short = mm(200)
    indications = (slag(at=0, length=20), slag(at=150, length=20))
    found = worst_window(indications, short)
    assert found.length == short
    assert found.aggregate == mm(40)
    assert not window_applies(short)


def test_a_long_weld_uses_the_standard_window():
    assert window_applies(mm(400))
    assert window_applies(STANDARD_WINDOW)
    assert not window_applies(mm(300))


def test_the_short_weld_rule_compares_a_fraction_instead():
    assert fraction_of_weld(mm(16), mm(200)) == Decimal("0.08")
    assert fraction_of_weld(mm(10), mm(200)) < Decimal("0.08")
    with pytest.raises(ValidationError):
        fraction_of_weld(mm(10), Length.ZERO)


def test_the_worst_window_never_runs_off_the_end_of_the_weld():
    indications = (slag(at=950, length=40),)
    found = worst_window(indications, mm(1000))
    assert found.ends_at <= mm(1000)
    assert found.aggregate == mm(40)


def test_some_rules_count_indications_rather_than_measuring_them():
    scattered = tuple(slag(at=i * 60, length=5) for i in range(8))
    assert count_in_worst_window(scattered, mm(1000)) == 6
    assert count_in_worst_window((), mm(1000)) == 0


def test_the_count_finds_the_worst_position_too():
    indications = (
        slag(at=0, length=5),
        slag(at=600, length=5),
        slag(at=620, length=5),
        slag(at=640, length=5),
        slag(at=660, length=5),
    )
    assert count_in_worst_window(indications, mm(1200)) == 4


def test_the_window_prints_the_way_the_report_reads():
    found = worst_window((slag(at=100, length=20),), mm(1000))
    assert "20.0 mm in the 305 mm from" in found.describe()
