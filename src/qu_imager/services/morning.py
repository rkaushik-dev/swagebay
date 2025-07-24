"""The morning sheet, which is one piece of paper the coordinator works from.

Everything on it already exists somewhere and that is the problem. The devices
are in the maintenance register, the sources are on the inventory, the films
waiting to be read are on the jobs, the reports are on the non conformance
register, and the wipe tests are due on their own schedule. Somebody was
opening five listings at seven in the morning and holding the answers in their
head.

The sheet has one rule about ordering that is not obvious. It leads with what
stops work today, then what stops work this week, then what is merely late.
A sheet sorted by record type puts an expired wipe test under W and a truck
that cannot leave under D, and the truck is the thing.

Nothing here decides anything. Every line is a question already answered
elsewhere, gathered.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from qu_imager.persistence.source_mapper import row_to_source
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.services.dispatch import Yard, clearance

THIS_WEEK = 7


@dataclass(frozen=True, slots=True)
class Line:
    """One thing somebody has to do something about, and how soon."""

    urgency: str
    subject: str
    account: str

    def describe(self) -> str:
        return f"{self.subject}: {self.account}"


@dataclass(frozen=True, slots=True)
class Sheet:
    """The whole morning, in the order it has to be worked through."""

    day: date
    stops_today: tuple[Line, ...]
    stops_this_week: tuple[Line, ...]
    running_late: tuple[Line, ...]

    @property
    def lines(self) -> tuple[Line, ...]:
        return self.stops_today + self.stops_this_week + self.running_late

    @property
    def is_quiet(self) -> bool:
        return not self.lines

    def describe(self) -> str:
        if self.is_quiet:
            return f"nothing outstanding on {self.day}"
        return f"{len(self.lines)} outstanding on {self.day}"


def sheet_for(work: UnitOfWork, day: date) -> Sheet:
    """Build the morning sheet from every register at once."""
    today: list[Line] = []
    week: list[Line] = []
    late: list[Line] = []

    yard = Yard.load(work)
    for serial in sorted(yard.devices):
        due = yard.register.quarterly_due_by(serial, day)
        if due is None:
            today.append(Line("today", serial, "no quarterly inspection on record"))
        elif day >= due:
            today.append(Line("today", serial, f"quarterly ran out on {due}"))
        elif due - day <= timedelta(days=THIS_WEEK):
            week.append(Line("this week", serial, f"quarterly due by {due}"))

    for row in work.sources.in_service():
        source = row_to_source(row)
        latest = source.latest_leak_test()
        if latest is None:
            today.append(Line("today", row.serial, "no wipe test on record"))
            continue
        expires = latest.expires_at().date()
        if day >= expires:
            today.append(Line("today", row.serial, f"wipe test ran out on {expires}"))
        elif expires - day <= timedelta(days=THIS_WEEK):
            week.append(Line("this week", row.serial, f"wipe test due by {expires}"))

    for report in work.nonconformances.open_reports():
        if report.is_overdue(day):
            late.append(
                Line("late", report.reference, f"open {report.days_open(day)} days on {report.job}")
            )
        elif report.disposition is None:
            week.append(Line("this week", report.reference, f"undecided on {report.job}"))

    return Sheet(
        day=day,
        stops_today=tuple(today),
        stops_this_week=tuple(week),
        running_late=tuple(late),
    )


def loads_ready(work: UnitOfWork, day: date) -> tuple[tuple[str, str], ...]:
    """Every device and source pairing that could actually leave today."""
    yard = Yard.load(work)
    ready = []
    for device in sorted(yard.devices):
        for serial in sorted(yard.sources):
            if clearance(work, device, serial, day, (), "yard", yard).cleared:
                ready.append((device, serial))
    return tuple(sorted(ready))


def films_waiting(work: UnitOfWork, day: date) -> tuple[str, ...]:
    """Films shot and not yet read, which is what the reader starts on."""
    waiting: list[str] = []
    for job in work.jobs.open_on(day):
        waiting.extend(row.number for row in work.welds.unread(job))
    return tuple(sorted(waiting))
