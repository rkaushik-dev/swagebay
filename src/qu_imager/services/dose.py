"""Dose reporting, which is a monthly job and an annual argument.

The ledger holds entries. Everything anybody wants to see is a sum over it, and
the sums are all slightly different, which is why they are here rather than in
the ledger.

The monthly report is per person for one month and it is what gets posted on
the board. The annual position is per person for the year to date and it is
what the investigation levels are read against. The two disagree constantly and
neither is wrong: somebody can have a quiet year and a bad month.

Entries are never edited. A correction is another entry, which can be negative
in the sense that the laboratory revised a badge reading downward, and that is
recorded as a separate entry with a reason rather than by changing the first
one. So the ledger only ever grows, and the sum is always the truth as of the
moment it was taken.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

from sqlalchemy import func, select

from qu_imager.errors import ValidationError
from qu_imager.persistence.models.safety import DoseEntryRow
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.quantities.dose import Dose
from qu_imager.safety.dosimetry import (
    INVESTIGATION_LEVEL_ONE,
    INVESTIGATION_LEVEL_TWO,
    WorkerClass,
)


@dataclass(frozen=True, slots=True)
class Position:
    """One person's dose position at a moment."""

    badge: str
    year: int
    taken: Dose
    limit: Dose
    worker_class: WorkerClass

    @property
    def fraction(self) -> Decimal:
        return self.taken.fraction_of(self.limit)

    @property
    def investigation_level(self) -> int:
        if self.fraction >= INVESTIGATION_LEVEL_TWO:
            return 2
        if self.fraction >= INVESTIGATION_LEVEL_ONE:
            return 1
        return 0

    @property
    def remaining(self) -> Dose:
        if self.taken >= self.limit:
            return Dose.ZERO
        return self.limit - self.taken

    def describe(self) -> str:
        return (
            f"{self.badge} {self.taken.format_msv(2)} of {self.limit.format_msv(0)}, "
            f"level {self.investigation_level}"
        )


def record_dose(
    work: UnitOfWork,
    badge: str,
    year: int,
    taken: Dose,
    recorded_at: datetime,
    source_of_entry: str,
) -> None:
    """Append an entry. Nothing here ever edits one."""
    if not badge.strip():
        raise ValidationError("a dose is recorded against a badge")
    if not source_of_entry.strip():
        raise ValidationError("a dose entry says where it came from")
    work.session.add(
        DoseEntryRow(
            badge=badge,
            year=year,
            taken=taken,
            recorded_at=recorded_at,
            source_of_entry=source_of_entry,
        )
    )
    work.flush()


def taken_in_year(work: UnitOfWork, badge: str, year: int) -> Dose:
    total = work.session.scalar(
        select(func.sum(DoseEntryRow.taken)).where(
            DoseEntryRow.badge == badge, DoseEntryRow.year == year
        )
    )
    return total if total is not None else Dose.ZERO


def taken_in_month(work: UnitOfWork, badge: str, year: int, month: int) -> Dose:
    if not 1 <= month <= 12:
        raise ValidationError("a month is one to twelve", month=month)
    start = datetime(year, month, 1)
    end = datetime(year + (month == 12), (month % 12) + 1, 1)
    total = work.session.scalar(
        select(func.sum(DoseEntryRow.taken)).where(
            DoseEntryRow.badge == badge,
            DoseEntryRow.recorded_at >= start,
            DoseEntryRow.recorded_at < end,
        )
    )
    return total if total is not None else Dose.ZERO


def position(
    work: UnitOfWork,
    badge: str,
    year: int,
    worker_class: WorkerClass = WorkerClass.ADULT,
) -> Position:
    return Position(
        badge=badge,
        year=year,
        taken=taken_in_year(work, badge, year),
        limit=worker_class.limit,
        worker_class=worker_class,
    )


def badges_in_year(work: UnitOfWork, year: int) -> tuple[str, ...]:
    rows = work.session.scalars(
        select(DoseEntryRow.badge).where(DoseEntryRow.year == year).distinct()
    ).all()
    return tuple(sorted(rows))


def over_investigation_level(work: UnitOfWork, year: int, level: int = 1) -> tuple[Position, ...]:
    """Everybody at or over an investigation level, worst first."""
    if level not in {1, 2}:
        raise ValidationError("there are two investigation levels", level=level)
    found = [position(work, badge, year) for badge in badges_in_year(work, year)]
    over = [p for p in found if p.investigation_level >= level]
    return tuple(sorted(over, key=lambda p: p.taken.nsv, reverse=True))


def monthly_sheet(work: UnitOfWork, year: int, month: int) -> tuple[tuple[str, Dose], ...]:
    """What goes on the board, in badge order."""
    return tuple(
        (badge, taken_in_month(work, badge, year, month)) for badge in badges_in_year(work, year)
    )


def collective_dose(work: UnitOfWork, year: int) -> Dose:
    """The whole shop for the year, which is the number the licence reports."""
    total = work.session.scalar(
        select(func.sum(DoseEntryRow.taken)).where(DoseEntryRow.year == year)
    )
    return total if total is not None else Dose.ZERO


def days_left_at_this_rate(position: Position, days_elapsed: int) -> int | None:
    """A crude burn down, which is what a supervisor actually wants in June."""
    if days_elapsed <= 0:
        raise ValidationError("some of the year has to have elapsed", days=days_elapsed)
    if position.taken.nsv == 0:
        return None
    per_day = Decimal(position.taken.nsv) / days_elapsed
    return int(Decimal(position.remaining.nsv) / per_day)
