"""The daily log, which is the one document that gets read after an incident.

Everything else in this package is a record of a thing. The daily log is a
record of a shift: who was on it, what device went out, what was shot, what the
surveys said and what went wrong. It is written at the time and it is the first
thing anybody asks for when something has gone wrong, precisely because it was
written before anybody knew that.

So it is assembled from the record rather than typed, and the parts that cannot
be assembled are the parts that have to be typed: the crew, the weather, and
the account of anything unusual. Mixing those two is what produces a log that
contradicts the records it sits beside.

A log is closed at the end of the shift and then it is not editable. An
addition after the fact is a separate note with its own timestamp, which is
what a lawyer would expect and what a shift supervisor would do on paper.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime

from sqlalchemy import select

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.models.safety import SurveyRow
from qu_imager.persistence.models.work import RadiographRow, WeldRow
from qu_imager.persistence.unit_of_work import UnitOfWork


@dataclass(frozen=True, slots=True)
class Note:
    """Something added after the log was closed, with its own time on it."""

    written_at: datetime
    written_by: str
    text: str

    def __post_init__(self) -> None:
        if not self.written_by.strip() or not self.text.strip():
            raise ValidationError("a note is signed and says something")


@dataclass(slots=True)
class DayLog:
    """One shift, assembled from the record and topped up by hand."""

    day: date
    site: str
    crew: tuple[str, ...]
    device_serial: str
    source_serial: str
    weather: str = ""
    account: str = ""
    closed_at: datetime | None = None
    notes: list[Note] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not self.site.strip():
            raise ValidationError("a log names the site")
        if not self.crew:
            raise ValidationError("a log names the crew")

    @property
    def is_closed(self) -> bool:
        return self.closed_at is not None

    def close(self, at: datetime) -> None:
        if self.closed_at is not None:
            raise ConflictError("that log is already closed", day=self.day.isoformat())
        self.closed_at = at

    def add_note(self, note: Note) -> None:
        """A note may be added at any time. Nothing already written changes."""
        if self.closed_at is not None and note.written_at < self.closed_at:
            raise ValidationError("a note added after the log closed cannot be dated before it")
        self.notes.append(note)

    def set_account(self, text: str) -> None:
        if self.is_closed:
            raise ConflictError("a closed log is not edited, add a note instead")
        self.account = text

    def describe(self) -> str:
        state = "closed" if self.is_closed else "open"
        return f"{self.day.isoformat()} {self.site}, {len(self.crew)} on crew, {state}"


def shots_on(work: UnitOfWork, day: date) -> tuple[RadiographRow, ...]:
    """Every exposure made that day, whatever job it was on."""
    start = datetime.combine(day, datetime.min.time())
    end = datetime.combine(day, datetime.max.time())
    return tuple(
        work.session.scalars(
            select(RadiographRow)
            .where(RadiographRow.exposed_at >= start, RadiographRow.exposed_at <= end)
            .order_by(RadiographRow.exposed_at, RadiographRow.number)
        ).all()
    )


def surveys_on(work: UnitOfWork, day: date) -> tuple[SurveyRow, ...]:
    start = datetime.combine(day, datetime.min.time())
    end = datetime.combine(day, datetime.max.time())
    return tuple(
        work.session.scalars(
            select(SurveyRow)
            .where(SurveyRow.performed_at >= start, SurveyRow.performed_at <= end)
            .order_by(SurveyRow.performed_at)
        ).all()
    )


def welds_shot_on(work: UnitOfWork, day: date) -> tuple[str, ...]:
    rows = shots_on(work, day)
    welds: list[str] = []
    for row in rows:
        weld: WeldRow = row.weld
        label = f"{weld.prefix}-{weld.number}"
        if weld.repair_round:
            label = f"{label}R{weld.repair_round}"
        if label not in welds:
            welds.append(label)
    return tuple(welds)


def gaps_on(work: UnitOfWork, day: date) -> tuple[str, ...]:
    """What the record says is missing for that day, which the log has to explain."""
    out: list[str] = []
    surveyed = {row.radiograph_number for row in surveys_on(work, day)}
    for row in shots_on(work, day):
        if row.number not in surveyed:
            out.append(f"{row.number} has no survey")
        if not row.admissible:
            out.append(f"{row.number} was not admissible")
    return tuple(out)


def summary(work: UnitOfWork, log: DayLog) -> dict[str, object]:
    """The assembled half of the log, which nobody types."""
    return {
        "day": log.day,
        "site": log.site,
        "crew": log.crew,
        "device": log.device_serial,
        "source": log.source_serial,
        "exposures": len(shots_on(work, log.day)),
        "welds": welds_shot_on(work, log.day),
        "surveys": len(surveys_on(work, log.day)),
        "gaps": gaps_on(work, log.day),
        "closed": log.is_closed,
    }
