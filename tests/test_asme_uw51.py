from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.asme_uw51 import (
    LONG_ALLOWANCE,
    SHORT_ALLOWANCE,
    THICK_LIMIT,
    THIN_LIMIT,
    elongated_limit,
    evaluate,
    fraction_of_wall,
    group_aligned,
    sliding_limit_is_continuous,
    window_for,
)
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length, inch, mm


def lof(at, length):
    return Indication(Kind.IF, mm(at), mm(length))


def test_thin_wall_gets_the_flat_quarter_inch():
    assert elongated_limit(mm(5)) == SHORT_ALLOWANCE
    assert elongated_limit(THIN_LIMIT) == inch("0.25")


def test_heavy_wall_gets_the_flat_three_quarters():
    assert elongated_limit(mm(80)) == LONG_ALLOWANCE
    assert elongated_limit(THICK_LIMIT) == inch("0.75")


def test_the_middle_is_a_third_of_the_wall():
    assert elongated_limit(mm(30)) == mm(10)
    assert elongated_limit(mm(45)) == mm(15)


def test_the_three_parts_join_up_at_both_ends():
    assert sliding_limit_is_continuous()
    assert elongated_limit(THIN_LIMIT) == elongated_limit(Length(THIN_LIMIT.um + 1))


def test_a_wall_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        elongated_limit(Length.ZERO)
    with pytest.raises(ValidationError):
        window_for(Length.ZERO)


def test_the_window_moves_with_the_wall_rather_than_being_twelve_inches():
    assert window_for(mm(10)) == mm(120)
    assert window_for(mm(25)) == mm(300)
    assert window_for(mm(10)) != inch(12)


def test_a_crack_is_out_whatever_its_length():
    findings = evaluate((Indication(Kind.C, mm(10), mm(2)),), mm(20))
    assert len(findings) == 1
    assert findings[0].rule == "b1"
    assert "not acceptable at any length" in findings[0].reason


def test_a_short_lack_of_fusion_is_accepted():
    assert evaluate((lof(at=10, length=5),), mm(20)) == ()


def test_a_long_lack_of_fusion_is_not():
    findings = evaluate((lof(at=10, length=10),), mm(20))
    assert len(findings) == 1
    assert findings[0].rule == "b2"
    assert "over the" in findings[0].reason


def test_the_same_indication_passes_on_heavier_wall():
    found = lof(at=10, length=10)
    assert evaluate((found,), mm(20)) != ()
    assert evaluate((found,), mm(40)) == ()


def test_volumetric_indications_are_not_judged_by_the_elongated_rule():
    slag = Indication(Kind.ESI, mm(10), mm(40), mm(1))
    assert evaluate((slag,), mm(20)) == ()


def test_close_indications_are_grouped_together():
    groups = group_aligned((lof(at=0, length=5), lof(at=20, length=5)))
    assert len(groups) == 1
    assert len(groups[0]) == 2
    assert groups[0].aggregate == mm(10)


def test_a_gap_over_six_times_the_longest_breaks_the_group():
    groups = group_aligned((lof(at=0, length=5), lof(at=100, length=5)))
    assert len(groups) == 2


def test_one_long_indication_pulls_its_neighbours_in():
    tight = group_aligned((lof(at=0, length=5), lof(at=100, length=5)))
    loose = group_aligned((lof(at=0, length=30), lof(at=100, length=5)))
    assert len(tight) == 2
    assert len(loose) == 1


def test_the_group_reports_its_longest_and_its_span():
    group = group_aligned((lof(at=0, length=5), lof(at=20, length=8)))[0]
    assert group.longest == mm(8)
    assert group.span == mm(28)


def test_an_empty_set_makes_no_groups():
    assert group_aligned(()) == ()


def test_aligned_indications_totalling_over_the_wall_are_a_finding():
    aligned = (lof(at=0, length=4), lof(at=20, length=4), lof(at=40, length=4))
    findings = evaluate(aligned, mm(10))
    assert any(f.rule == "b3" for f in findings)
    assert any("aligned indications total" in f.reason for f in findings)


def test_the_same_three_on_heavier_wall_are_accepted():
    aligned = (lof(at=0, length=4), lof(at=20, length=4), lof(at=40, length=4))
    assert evaluate(aligned, mm(20)) == ()


def test_a_single_indication_is_never_an_aligned_group():
    findings = evaluate((lof(at=0, length=4),), mm(10))
    assert not any(f.rule == "b3" for f in findings)


def test_the_fraction_of_the_wall_is_what_the_report_quotes():
    assert fraction_of_wall(lof(at=0, length=5), mm(10)) == Decimal("0.5")
    with pytest.raises(ValidationError):
        fraction_of_wall(lof(at=0, length=5), Length.ZERO)


def test_the_findings_name_the_subclause():
    findings = evaluate((lof(at=10, length=10),), mm(20))
    assert findings[0].describe().startswith("UW-51(b2): ")
