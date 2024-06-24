from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.assessment import Disposition, assess
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length, inch, mm

WALL = mm(10)


def weld(*indications, length=1000):
    return IndicationSet(mm(length), indications)


def slag(at=100, length=20, width=1, kind=Kind.ESI):
    return Indication(kind, mm(at), mm(length), mm(width))


def test_a_clean_weld_is_accepted():
    verdict = assess(weld(), WALL)
    assert verdict.accepted
    assert verdict.findings == ()
    assert verdict.describe() == "accept"


def test_a_short_inclusion_inside_every_limit_is_accepted():
    assert assess(weld(slag(length=20, width=1)), WALL).accepted


def test_an_inclusion_over_the_length_limit_is_a_repair():
    verdict = assess(weld(slag(length=60)), WALL)
    assert verdict.disposition is Disposition.REPAIR
    assert "9.3.8" in verdict.clauses()
    assert "over the" in verdict.findings[0].reason


def test_an_inclusion_over_the_width_limit_fails_on_width_alone():
    verdict = assess(weld(slag(length=20, width=3)), WALL)
    assert not verdict.accepted
    assert "across" in verdict.findings[0].reason


def test_the_aggregate_rule_catches_what_the_individual_rule_lets_through():
    bunched = tuple(slag(at=i * 30, length=20) for i in range(4))
    verdict = assess(weld(*bunched), WALL)
    assert not verdict.accepted
    assert any("in 305 mm" in f.reason for f in verdict.findings)


def test_the_same_inclusions_spread_out_are_accepted():
    spread = tuple(slag(at=i * 350, length=20) for i in range(4))
    assert assess(weld(*spread, length=1500), WALL).accepted


def test_a_crack_is_rejected_rather_than_repaired():
    verdict = assess(weld(Indication(Kind.C, mm(100), mm(10))), WALL)
    assert verdict.disposition is Disposition.REJECT
    assert "9.3.10" in verdict.clauses()


def test_a_shallow_crater_crack_under_four_millimetres_is_the_one_exception():
    assert assess(weld(Indication(Kind.C, mm(100), mm(3))), WALL).accepted


def test_a_crack_rejects_the_weld_even_alongside_repairable_findings():
    verdict = assess(weld(Indication(Kind.C, mm(100), mm(10)), slag(at=400, length=60)), WALL)
    assert verdict.disposition is Disposition.REJECT
    assert len(verdict.findings) == 3
    assert {f.kind for f in verdict.findings} == {Kind.C, Kind.ESI}


def test_internal_concavity_of_any_length_is_accepted():
    assert assess(weld(Indication(Kind.IC, mm(100), mm(200))), WALL).accepted


def test_burn_through_is_judged_against_the_wall_it_is_in():
    big = Indication(Kind.BT, mm(100), mm(8))
    assert not assess(weld(big), mm(10)).accepted
    assert assess(weld(big), mm(40)).accepted


def test_a_pore_is_judged_across_and_against_a_quarter_of_the_wall():
    fat = Indication(Kind.P, mm(100), Length.ZERO, mm(4))
    assert not assess(weld(fat), mm(10)).accepted
    small = Indication(Kind.P, mm(100), Length.ZERO, mm(2))
    assert assess(weld(small), mm(10)).accepted


def test_isolated_slag_is_capped_on_how_many_not_only_how_big():
    many = tuple(Indication(Kind.ISI, mm(i * 40), mm(2), mm(2)) for i in range(6))
    verdict = assess(weld(*many), WALL)
    assert not verdict.accepted
    assert any("in one window is over" in f.reason for f in verdict.findings)


def test_four_isolated_inclusions_are_allowed():
    few = tuple(Indication(Kind.ISI, mm(i * 40), mm(2), mm(2)) for i in range(4))
    assert assess(weld(*few), WALL).accepted


def test_a_short_weld_is_judged_on_a_percentage_instead_of_the_window():
    long_weld = weld(Indication(Kind.IP, mm(0), mm(20)), length=1000)
    assert assess(long_weld, WALL).accepted
    short_weld = weld(Indication(Kind.IP, mm(0), mm(20)), length=200)
    verdict = assess(short_weld, WALL)
    assert not verdict.accepted
    assert "short weld" in verdict.findings[0].reason


def test_the_short_weld_rule_is_stricter_not_more_lenient():
    aggregate = Indication(Kind.IF, mm(0), mm(15))
    assert assess(weld(aggregate, length=1000), WALL).accepted
    assert not assess(weld(aggregate, length=150), WALL).accepted


def test_undercut_is_judged_on_depth_as_well_as_length():
    deep = Indication(Kind.U, mm(100), mm(20), depth_fraction=Decimal("0.2"))
    verdict = assess(weld(deep), mm(10))
    assert not verdict.accepted
    assert "deep is over" in verdict.findings[0].reason


def test_shallow_undercut_of_the_same_length_is_accepted():
    shallow = Indication(Kind.U, mm(100), mm(20), depth_fraction=Decimal("0.05"))
    assert assess(weld(shallow), mm(10)).accepted


def test_undercut_depth_is_read_against_the_wall_it_sits_in():
    same = Indication(Kind.U, mm(100), mm(20), depth_fraction=Decimal("0.1"))
    assert assess(weld(same), mm(4)).accepted
    assert not assess(weld(same), mm(20)).accepted


def test_undercut_with_no_depth_recorded_is_not_judged_on_depth():
    assert assess(weld(Indication(Kind.U, mm(100), mm(20))), mm(10)).accepted


def test_a_wall_of_nothing_is_refused_before_anything_is_assessed():
    with pytest.raises(ValidationError):
        assess(weld(), Length.ZERO)


def test_the_findings_name_the_clause_they_came_from():
    verdict = assess(weld(slag(length=60)), WALL)
    assert verdict.findings[0].describe().startswith("9.3.8 ESI: ")
    assert inch(2) < mm(60)
