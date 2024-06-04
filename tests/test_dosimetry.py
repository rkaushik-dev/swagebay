from datetime import date
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.dose import Dose, usv
from qu_imager.safety.dosimetry import (
    ANNUAL_LIMIT,
    Assignment,
    DoseLedger,
    Dosimeter,
    DosimeterKind,
    WorkerClass,
)

TODAY = date(2024, 6, 5)
DUE = date(2025, 1, 1)


def full_kit(due=DUE):
    return (
        Dosimeter(DosimeterKind.BADGE, "B-4471"),
        Dosimeter(DosimeterKind.DIRECT_READING, "D-118", due),
        Dosimeter(DosimeterKind.ALARMING_RATEMETER, "A-77", due),
    )


def assigned(badge="0142", worker=WorkerClass.ADULT, kit=None):
    return Assignment(badge=badge, worker_class=worker, dosimeters=kit or full_kit())


def test_a_full_kit_gets_somebody_inside_the_boundary():
    assert assigned().may_enter_boundary_on(TODAY)
    assert assigned().missing_on(TODAY) == ()


def test_every_missing_instrument_is_named():
    bare = Assignment(badge="0201")
    missing = bare.missing_on(TODAY)
    assert len(missing) == 3
    assert "no alarming ratemeter" in missing
    assert not bare.may_enter_boundary_on(TODAY)


def test_the_alarm_is_the_one_that_can_save_anybody():
    without_alarm = assigned(kit=full_kit()[:2])
    assert not without_alarm.may_enter_boundary_on(TODAY)
    assert without_alarm.missing_on(TODAY) == ("no alarming ratemeter",)
    assert DosimeterKind.ALARMING_RATEMETER.warns_before_the_dose
    assert not DosimeterKind.BADGE.warns_before_the_dose


def test_only_the_badge_says_nothing_on_the_day():
    assert not DosimeterKind.BADGE.reads_on_the_day
    assert DosimeterKind.DIRECT_READING.reads_on_the_day
    assert DosimeterKind.ALARMING_RATEMETER.reads_on_the_day


def test_an_instrument_out_of_calibration_counts_as_missing():
    stale = assigned(kit=full_kit(due=date(2024, 1, 1)))
    missing = stale.missing_on(TODAY)
    assert len(missing) == 2
    assert all("out of calibration" in m for m in missing)


def test_a_badge_needs_no_calibration_date_and_the_others_do():
    Dosimeter(DosimeterKind.BADGE, "B-1")
    with pytest.raises(ValidationError):
        Dosimeter(DosimeterKind.ALARMING_RATEMETER, "A-1")
    with pytest.raises(ValidationError):
        Dosimeter(DosimeterKind.BADGE, "   ")


def test_dose_accumulates_across_a_year():
    ledger = DoseLedger(year=2024)
    ledger.record("0142", usv(500))
    ledger.record("0142", usv(300))
    assert ledger.taken_by("0142") == usv(800)
    assert ledger.taken_by("0201") == Dose.ZERO


def test_a_dose_needs_a_badge_to_go_against():
    ledger = DoseLedger(year=2024)
    with pytest.raises(ValidationError):
        ledger.record("  ", usv(500))


def test_the_investigation_levels_fire_long_before_the_limit():
    ledger = DoseLedger(year=2024)
    person = assigned()
    assert ledger.investigation_level(person) == 0
    ledger.record("0142", Dose.from_msv(6))
    assert ledger.investigation_level(person) == 1
    ledger.record("0142", Dose.from_msv(10))
    assert ledger.investigation_level(person) == 2
    assert not ledger.over_limit(person)


def test_the_annual_limit_is_where_the_law_puts_it():
    assert ANNUAL_LIMIT == Dose.from_msv(50)
    assert WorkerClass.ADULT.limit == ANNUAL_LIMIT


def test_a_declared_pregnant_worker_is_held_to_a_tenth_of_it():
    ledger = DoseLedger(year=2024)
    expecting = assigned(badge="0303", worker=WorkerClass.DECLARED_PREGNANT)
    ledger.record("0303", Dose.from_msv(4))
    assert ledger.investigation_level(expecting) == 2
    assert not ledger.over_limit(expecting)
    ledger.record("0303", Dose.from_msv(2))
    assert ledger.over_limit(expecting)


def test_the_same_dose_reads_differently_against_the_two_limits():
    ledger = DoseLedger(year=2024)
    ledger.record("0142", Dose.from_msv(4))
    ledger.record("0303", Dose.from_msv(4))
    adult = assigned(badge="0142")
    expecting = assigned(badge="0303", worker=WorkerClass.DECLARED_PREGNANT)
    assert ledger.investigation_level(adult) == 0
    assert ledger.investigation_level(expecting) == 2


def test_what_is_left_of_the_year_is_reported():
    ledger = DoseLedger(year=2024)
    person = assigned()
    assert ledger.remaining(person) == ANNUAL_LIMIT
    ledger.record("0142", Dose.from_msv(20))
    assert ledger.remaining(person) == Dose.from_msv(30)
    ledger.record("0142", Dose.from_msv(40))
    assert ledger.remaining(person) == Dose.ZERO


def test_the_fraction_taken_is_what_the_monthly_report_prints():
    ledger = DoseLedger(year=2024)
    ledger.record("0142", Dose.from_msv(5))
    assert ledger.fraction_for(assigned()) == Decimal("0.1")


def test_the_ledger_lists_who_it_holds():
    ledger = DoseLedger(year=2024)
    ledger.record("0201", usv(10))
    ledger.record("0142", usv(10))
    assert ledger.badges() == ("0142", "0201")
    assert str(WorkerClass.MINOR) == "minor"
