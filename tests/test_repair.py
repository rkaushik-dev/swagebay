from datetime import date
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.repair import (
    REPAIR_MARGIN,
    Excavation,
    Repair,
    RepairRegister,
    ReShoot,
)
from qu_imager.identity import WeldId
from qu_imager.quantities.length import Length, mm

WHEN = date(2024, 7, 8)


def repair(weld="GW-104R1", start=200, end=260, approved=False):
    return Repair(
        weld=WeldId.parse(weld),
        excavation=Excavation(mm(start), mm(end)),
        welded_by="w-19",
        welded_on=WHEN,
        client_approved=approved,
    )


def test_the_re_shoot_has_to_reach_past_the_excavation():
    cut = Excavation(mm(200), mm(260))
    start, end = cut.coverage_required()
    assert start == mm(175)
    assert end == mm(285)
    assert REPAIR_MARGIN == mm(25)


def test_the_margin_is_clipped_at_the_start_of_the_weld():
    cut = Excavation(mm(10), mm(60))
    start, _end = cut.coverage_required()
    assert start == Length.ZERO


def test_a_re_shoot_that_reaches_covers_the_repair():
    assert ReShoot(WeldId.parse("GW-104R1"), mm(150), mm(300)).covers(repair())


def test_a_re_shoot_that_covers_only_the_excavation_does_not():
    tight = ReShoot(WeldId.parse("GW-104R1"), mm(200), mm(260))
    assert not tight.covers(repair())
    before, after = tight.shortfall(repair())
    assert before == mm(25)
    assert after == mm(25)


def test_a_re_shoot_short_at_one_end_only_reports_one_shortfall():
    lopsided = ReShoot(WeldId.parse("GW-104R1"), mm(100), mm(270))
    before, after = lopsided.shortfall(repair())
    assert before == Length.ZERO
    assert after == mm(15)


def test_a_re_shoot_that_reaches_reports_no_shortfall():
    assert ReShoot(WeldId.parse("GW-104R1"), mm(100), mm(400)).shortfall(repair()) == (
        Length.ZERO,
        Length.ZERO,
    )


def test_an_excavation_with_no_length_is_refused():
    with pytest.raises(ValidationError):
        Excavation(mm(200), mm(200))
    with pytest.raises(ValidationError):
        Excavation(mm(200), mm(100))
    with pytest.raises(ValidationError):
        Excavation(Length(-1), mm(100))


def test_a_re_shoot_with_no_length_is_refused():
    with pytest.raises(ValidationError):
        ReShoot(WeldId.parse("GW-104R1"), mm(100), mm(100))


def test_a_repair_belongs_to_a_repair_round_not_the_original():
    with pytest.raises(ValidationError) as caught:
        repair(weld="GW-104")
    assert caught.value.context["weld"] == "GW-104"


def test_a_repair_is_signed_by_the_welder():
    with pytest.raises(ValidationError):
        Repair(
            weld=WeldId.parse("GW-104R1"),
            excavation=Excavation(mm(200), mm(260)),
            welded_by="  ",
            welded_on=WHEN,
        )


def test_the_first_repair_needs_no_approval():
    register = RepairRegister()
    register.record(repair())
    assert register.rounds_for(WeldId.parse("GW-104")) == (1,)


def test_a_second_repair_of_the_same_weld_needs_the_client():
    register = RepairRegister()
    register.record(repair())
    with pytest.raises(ConflictError) as caught:
        register.record(repair(weld="GW-104R2"))
    assert caught.value.context["round"] == 2
    register.record(repair(weld="GW-104R2", approved=True))
    assert register.rounds_for(WeldId.parse("GW-104")) == (1, 2)


def test_the_same_round_cannot_be_recorded_twice():
    register = RepairRegister()
    register.record(repair())
    with pytest.raises(ConflictError):
        register.record(repair())


def test_a_repair_is_a_round_of_the_weld_and_not_a_new_one():
    register = RepairRegister()
    register.record(repair())
    register.record(repair(weld="GW-104R2", approved=True))
    assert register.repaired_welds() == frozenset({WeldId.parse("GW-104")})
    assert len(register.repairs) == 2


def test_the_rate_counts_welds_and_not_repairs():
    register = RepairRegister()
    register.record(repair())
    register.record(repair(weld="GW-104R2", approved=True))
    register.record(repair(weld="GW-110R1"))
    assert register.rate_over(100) == Decimal("0.02")


def test_a_rate_needs_a_denominator():
    register = RepairRegister()
    with pytest.raises(ValidationError):
        register.rate_over(0)


def test_the_welds_that_failed_twice_are_reported_on_their_own():
    register = RepairRegister()
    register.record(repair())
    register.record(repair(weld="GW-104R2", approved=True))
    register.record(repair(weld="GW-110R1"))
    assert [str(w) for w in register.repeat_offenders()] == ["GW-104"]


def test_a_register_with_nothing_in_it_reports_nothing():
    register = RepairRegister()
    assert register.repaired_welds() == frozenset()
    assert register.repeat_offenders() == ()
    assert register.rate_over(50) == Decimal(0)
