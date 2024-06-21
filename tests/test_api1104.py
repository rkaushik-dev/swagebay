from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.evaluation.api1104 import (
    ACCUMULATION_FRACTION,
    UNDERCUT_ALWAYS_ACCEPTABLE,
    WINDOW,
    all_criteria,
    criterion,
    undercut_depth_limit,
)
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length, inch, mm


def test_the_window_every_aggregate_is_written_on():
    assert inch(12) == WINDOW
    assert ACCUMULATION_FRACTION == Decimal("0.08")


def test_inadequate_penetration_is_capped_at_an_inch_three_ways():
    row = criterion(Kind.IP)
    assert row.clause == "9.3.1"
    assert row.max_length == inch(1)
    assert row.max_aggregate == inch(1)
    assert row.short_weld_fraction == Decimal("0.08")


def test_penetration_with_high_low_is_allowed_more():
    plain = criterion(Kind.IP)
    with_high_low = criterion(Kind.IPD)
    assert with_high_low.max_length > plain.max_length
    assert with_high_low.max_aggregate > plain.max_aggregate


def test_elongated_slag_is_capped_on_width_as_well_as_length():
    row = criterion(Kind.ESI)
    assert row.max_length == inch(2)
    assert row.max_width == mm("1.6")
    assert row.max_aggregate == inch(2)


def test_isolated_slag_is_capped_on_width_and_on_count():
    row = criterion(Kind.ISI)
    assert row.max_width == mm(3)
    assert row.max_count == 4
    assert row.max_length is None


def test_a_crack_has_the_smallest_allowance_in_the_whole_table():
    row = criterion(Kind.C)
    assert row.max_length == mm(4)
    assert "crater" in row.note
    assert Kind.C.is_always_rejectable


def test_internal_concavity_has_no_length_limit_at_all():
    row = criterion(Kind.IC)
    assert row.max_length is None
    assert row.max_aggregate is None
    assert "any length" in row.note


def test_burn_through_binds_on_the_fraction_on_thin_wall():
    row = criterion(Kind.BT)
    assert row.individual_length_limit(mm(10)) == mm(5)
    assert row.individual_length_limit(mm(6)) == mm(3)


def test_burn_through_binds_on_the_fixed_number_on_heavy_wall():
    row = criterion(Kind.BT)
    assert row.individual_length_limit(mm(40)) == inch("0.5")
    assert row.individual_length_limit(mm(100)) == inch("0.5")


def test_the_crossover_is_where_the_two_numbers_meet():
    row = criterion(Kind.BT)
    crossover = inch("0.5") * 2
    assert row.individual_length_limit(crossover) == inch("0.5")
    assert row.individual_length_limit(Length(crossover.um - 1000)) < inch("0.5")


def test_a_pore_is_capped_at_three_millimetres_or_a_quarter_of_the_wall():
    row = criterion(Kind.P)
    assert row.max_width == mm(3)
    assert row.individual_length_limit(mm(8)) == mm(2)
    assert row.individual_length_limit(mm(40)) is not None


def test_a_row_with_no_fraction_returns_its_fixed_limit_unchanged():
    row = criterion(Kind.IP)
    assert row.individual_length_limit(mm(6)) == inch(1)
    assert row.individual_length_limit(mm(60)) == inch(1)


def test_a_wall_of_nothing_is_refused_before_any_lookup():
    with pytest.raises(ValidationError):
        criterion(Kind.BT).individual_length_limit(Length.ZERO)
    with pytest.raises(ValidationError):
        undercut_depth_limit(Length.ZERO)


def test_undercut_depth_binds_on_the_fraction_on_thin_wall():
    assert undercut_depth_limit(mm(4)) == mm("0.5")
    assert undercut_depth_limit(mm(6)) == mm("0.75")


def test_undercut_depth_binds_on_the_fixed_number_on_heavy_wall():
    assert undercut_depth_limit(mm(10)) == mm("0.8")
    assert undercut_depth_limit(mm(30)) == mm("0.8")
    assert UNDERCUT_ALWAYS_ACCEPTABLE < mm("0.8")


def test_every_kind_in_the_taxonomy_has_a_row():
    covered = {row.kind for row in all_criteria()}
    assert covered == set(Kind)


def test_every_row_names_the_clause_it_came_from():
    for row in all_criteria():
        assert row.clause.startswith("9.3.")


def test_a_kind_with_no_row_is_refused_rather_than_defaulted():
    class Fake:
        code = "ZZ"

    with pytest.raises(ConfigurationError):
        criterion(Fake())  # type: ignore[arg-type]
