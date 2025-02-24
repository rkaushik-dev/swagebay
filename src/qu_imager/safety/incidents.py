"""Abnormal occurrences, which are reported on a clock rather than when convenient.

Some things that go wrong on a radiography job have to be told to the regulator
and the clock starts when the shop finds out, not when it decides. A source
that will not retract is an immediate call. A dose over a limit is twenty four
hours. A lost or damaged device is twenty four hours. A survey meter found out
of calibration after the fact is a thirty day written report.

So an incident carries the moment it was discovered and its own class, and the
deadline falls out of both. Storing a deadline instead would let somebody
correct it, which is exactly the field that gets corrected when a report is
late.

Nothing here notifies anybody. It says what is due and when, and a person makes
the call, because a phone call to a regulator made by software is a phone call
nobody can answer questions on.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum

from qu_imager.errors import ConflictError, ValidationError


class Occurrence(Enum):
    """What happened, which sets the clock."""

    SOURCE_DISCONNECTED = "source will not retract"
    OVEREXPOSURE = "dose over a limit"
    LOST_DEVICE = "device lost or stolen"
    DAMAGED_DEVICE = "device damaged"
    UNCONTROLLED_AREA = "boundary breached by a member of the public"
    METER_OUT_OF_CALIBRATION = "survey meter found out of calibration"
    LEAK_TEST_FAILURE = "leak test over the limit"

    @property
    def deadline(self) -> timedelta:
        return _DEADLINES[self]

    @property
    def is_immediate(self) -> bool:
        return self.deadline <= timedelta(hours=1)

    @property
    def needs_a_written_report(self) -> bool:
        return self.deadline >= timedelta(days=30)

    def __str__(self) -> str:
        return self.value


_DEADLINES: dict[Occurrence, timedelta] = {
    Occurrence.SOURCE_DISCONNECTED: timedelta(hours=1),
    Occurrence.OVEREXPOSURE: timedelta(hours=24),
    Occurrence.LOST_DEVICE: timedelta(hours=24),
    Occurrence.DAMAGED_DEVICE: timedelta(hours=24),
    Occurrence.UNCONTROLLED_AREA: timedelta(hours=24),
    Occurrence.LEAK_TEST_FAILURE: timedelta(days=5),
    Occurrence.METER_OUT_OF_CALIBRATION: timedelta(days=30),
}


@dataclass(slots=True)
class Incident:
    """One thing that went wrong, and what is owed because of it."""

    reference: str
    occurrence: Occurrence
    discovered_at: datetime
    site: str
    reported_by: str
    account: str
    notified_at: datetime | None = None

    def __post_init__(self) -> None:
        for name, value in (
            ("reference", self.reference),
            ("site", self.site),
            ("reported_by", self.reported_by),
            ("account", self.account),
        ):
            if not value.strip():
                raise ValidationError("an incident needs this filled in", field=name)
        if self.notified_at is not None and self.notified_at < self.discovered_at:
            raise ValidationError(
                "an incident cannot be reported before it was found",
                reference=self.reference,
            )

    @property
    def due_at(self) -> datetime:
        return self.discovered_at + self.occurrence.deadline

    @property
    def is_notified(self) -> bool:
        return self.notified_at is not None

    def is_late_at(self, moment: datetime) -> bool:
        if self.notified_at is not None:
            return self.notified_at > self.due_at
        return moment > self.due_at

    def time_left_at(self, moment: datetime) -> timedelta:
        if self.notified_at is not None:
            return timedelta(0)
        return self.due_at - moment

    def notify(self, at: datetime) -> None:
        if self.notified_at is not None:
            raise ConflictError("that incident is already reported", reference=self.reference)
        if at < self.discovered_at:
            raise ValidationError("an incident cannot be reported before it was found")
        self.notified_at = at

    def describe(self) -> str:
        state = "reported" if self.is_notified else f"due {self.due_at:%Y-%m-%d %H:%M}"
        return f"{self.reference} {self.occurrence}, {state}"


@dataclass(slots=True)
class Register:
    """Every incident, and what is owed right now."""

    incidents: dict[str, Incident] = field(default_factory=dict)

    def record(self, incident: Incident) -> None:
        if incident.reference in self.incidents:
            raise ConflictError("that reference is already used", reference=incident.reference)
        self.incidents[incident.reference] = incident

    def get(self, reference: str) -> Incident:
        try:
            return self.incidents[reference]
        except KeyError:
            raise ConflictError("no incident with that reference", reference=reference) from None

    def outstanding_at(self, moment: datetime) -> tuple[Incident, ...]:
        """Everything not yet reported, soonest deadline first."""
        pending = [i for i in self.incidents.values() if not i.is_notified]
        return tuple(sorted(pending, key=lambda i: (i.due_at, i.reference)))

    def late_at(self, moment: datetime) -> tuple[str, ...]:
        return tuple(
            sorted(
                reference
                for reference, incident in self.incidents.items()
                if incident.is_late_at(moment)
            )
        )

    def immediate_at(self, moment: datetime) -> tuple[str, ...]:
        """The ones somebody should be on the phone about, reported or not."""
        return tuple(
            sorted(
                reference
                for reference, incident in self.incidents.items()
                if incident.occurrence.is_immediate and not incident.is_notified
            )
        )
