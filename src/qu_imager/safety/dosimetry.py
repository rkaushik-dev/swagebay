"""Who is wearing what, and how much they have taken.

A radiographer wears three things and they do three different jobs. The badge
is the legal record and it is read months later by a laboratory, so it tells
nobody anything on the day. The direct reading dosimeter can be looked at on
the spot, so it tells the crew what happened this shift. The alarming rate
meter is the only one that can save anybody, because it makes a noise before
the dose is taken rather than recording it afterwards.

All three are required and the requirement is not a preference. An assistant
without an alarm on his belt is not allowed inside the boundary, whatever the
badge situation is, and this module reports the three separately for that
reason.

Dose is tracked against the annual limit and against the investigation levels
under it. The investigation levels are the useful part. Nobody in this trade
gets near the legal limit, so the limit never fires and never tells anybody
anything. The level at a tenth of it fires two or three times a year, and every
time it does somebody finds out why.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.quantities.dose import Dose

ANNUAL_LIMIT = Dose.from_msv(50)
DECLARED_PREGNANT_LIMIT = Dose.from_msv(5)
MINOR_LIMIT = Dose.from_msv(5)

INVESTIGATION_LEVEL_ONE = Decimal("0.10")
INVESTIGATION_LEVEL_TWO = Decimal("0.30")


class DosimeterKind(Enum):
    BADGE = "personnel dosimeter"
    DIRECT_READING = "direct reading dosimeter"
    ALARMING_RATEMETER = "alarming ratemeter"

    @property
    def reads_on_the_day(self) -> bool:
        return self is not DosimeterKind.BADGE

    @property
    def warns_before_the_dose(self) -> bool:
        return self is DosimeterKind.ALARMING_RATEMETER

    def __str__(self) -> str:
        return self.value


class WorkerClass(Enum):
    ADULT = "adult worker"
    DECLARED_PREGNANT = "declared pregnant worker"
    MINOR = "minor"

    @property
    def limit(self) -> Dose:
        if self is WorkerClass.DECLARED_PREGNANT:
            return DECLARED_PREGNANT_LIMIT
        if self is WorkerClass.MINOR:
            return MINOR_LIMIT
        return ANNUAL_LIMIT

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Dosimeter:
    """One instrument issued to one person."""

    kind: DosimeterKind
    serial: str
    calibration_due: date | None = None

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise ValidationError("a dosimeter has a serial number")
        if self.kind is not DosimeterKind.BADGE and self.calibration_due is None:
            raise ValidationError(
                "an instrument that is read on the day needs a calibration date",
                kind=self.kind.value,
            )

    def is_current_on(self, day: date) -> bool:
        if self.calibration_due is None:
            return True
        return day < self.calibration_due


@dataclass(slots=True)
class Assignment:
    """What one person is carrying."""

    badge: str
    worker_class: WorkerClass = WorkerClass.ADULT
    dosimeters: tuple[Dosimeter, ...] = ()

    def kinds(self) -> frozenset[DosimeterKind]:
        return frozenset(d.kind for d in self.dosimeters)

    def missing_on(self, day: date) -> tuple[str, ...]:
        """Every instrument that is absent or out of calibration."""
        out: list[str] = []
        held = {d.kind: d for d in self.dosimeters}
        for kind in DosimeterKind:
            instrument = held.get(kind)
            if instrument is None:
                out.append(f"no {kind}")
            elif not instrument.is_current_on(day):
                out.append(f"{kind} {instrument.serial} is out of calibration")
        return tuple(out)

    def may_enter_boundary_on(self, day: date) -> bool:
        return not self.missing_on(day)


@dataclass(slots=True)
class DoseLedger:
    """What each person has taken this year."""

    year: int
    doses: dict[str, Dose] = field(default_factory=dict)

    def record(self, badge: str, taken: Dose) -> None:
        if not badge.strip():
            raise ValidationError("a dose is recorded against a badge")
        self.doses[badge] = self.doses.get(badge, Dose.ZERO) + taken

    def taken_by(self, badge: str) -> Dose:
        return self.doses.get(badge, Dose.ZERO)

    def fraction_for(self, assignment: Assignment) -> Decimal:
        return self.taken_by(assignment.badge).fraction_of(assignment.worker_class.limit)

    def investigation_level(self, assignment: Assignment) -> int:
        """Zero, one or two, which is what the monthly report is keyed on."""
        fraction = self.fraction_for(assignment)
        if fraction >= INVESTIGATION_LEVEL_TWO:
            return 2
        if fraction >= INVESTIGATION_LEVEL_ONE:
            return 1
        return 0

    def over_limit(self, assignment: Assignment) -> bool:
        return self.taken_by(assignment.badge) > assignment.worker_class.limit

    def remaining(self, assignment: Assignment) -> Dose:
        limit = assignment.worker_class.limit
        taken = self.taken_by(assignment.badge)
        if taken >= limit:
            return Dose.ZERO
        return limit - taken

    def badges(self) -> tuple[str, ...]:
        return tuple(sorted(self.doses))
