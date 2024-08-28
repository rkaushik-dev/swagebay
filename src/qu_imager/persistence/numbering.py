"""Issuing gapless numbers, and formatting them the way the paperwork does."""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import ValidationError
from qu_imager.persistence.models.counters import CounterRow

RADIOGRAPH = "radiograph"
REPORT = "report"


@dataclass(frozen=True, slots=True)
class Numbering:
    """A counter, read and advanced inside somebody else's transaction."""

    session: Session

    def take(self, name: str, scope: str = "") -> int:
        """The next number, advanced in this transaction and no other."""
        if not name.strip():
            raise ValidationError("a counter has a name")
        row = self.session.scalars(
            select(CounterRow)
            .where(CounterRow.name == name, CounterRow.scope == scope)
            .with_for_update()
        ).one_or_none()
        if row is None:
            row = CounterRow(name=name, scope=scope, next_value=1)
            self.session.add(row)
            self.session.flush()
        issued = row.next_value
        row.next_value = issued + 1
        self.session.flush()
        return issued

    def peek(self, name: str, scope: str = "") -> int:
        """What would be issued next, without taking it."""
        row = self.session.scalars(
            select(CounterRow).where(CounterRow.name == name, CounterRow.scope == scope)
        ).one_or_none()
        return 1 if row is None else row.next_value

    def take_radiograph_number(self, job: str) -> str:
        return format_radiograph(job, self.take(RADIOGRAPH, job))

    def take_report_number(self, job: str) -> str:
        return format_report(job, self.take(REPORT, job))


def format_radiograph(job: str, issued: int) -> str:
    if issued < 1:
        raise ValidationError("a radiograph number starts at one", issued=issued)
    return f"{job}-R{issued:04d}"


def format_report(job: str, issued: int) -> str:
    if issued < 1:
        raise ValidationError("a report number starts at one", issued=issued)
    return f"{job}-RPT{issued:03d}"
