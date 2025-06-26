"""Non conformance reports, which are not the same thing as a repair.

A repair round is a weld being cut out and rewelded, and the package already
tracks those. A non conformance report is the piece of paper that says work did
not meet the specification and records what was decided about it, which is a
different question with a different set of people signing.

The distinction that matters is the disposition. Four of them and only four,
because a shop that invents a fifth ends up with a category meaning "we talked
about it".

Repair and rework put the item back inside the specification, and repair needs
the client to agree to the procedure because a repair is a change to a welded
joint they are buying.

Use as is leaves work outside the specification in the line, which nobody in
this shop may decide alone. It needs a concession with a reference on it,
signed by whoever owns the specification.

Reject takes the item out, and the report is not closed until there is a
removal reference, because a rejected spool that is still on the rack is a
rejected spool that gets welded in on a Saturday.

Closure needs somebody other than the person who raised it. That is the whole
control. A report raised and closed by one person is a note to self.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ConflictError, ValidationError

ESCALATE_AFTER_DAYS = 30


class Disposition(Enum):
    REPAIR = "repair"
    REWORK = "rework"
    USE_AS_IS = "use as is"
    REJECT = "reject"

    @property
    def needs_concession(self) -> bool:
        """Anything that leaves or changes the specification needs a signature."""
        return self in {Disposition.USE_AS_IS, Disposition.REPAIR}

    @property
    def needs_removal(self) -> bool:
        return self is Disposition.REJECT

    def __str__(self) -> str:
        return self.value


@dataclass(slots=True)
class Report:
    """One non conformance, from raised to closed."""

    reference: str
    job: str
    item: str
    requirement: str
    account: str
    raised_on: date
    raised_by: str
    disposition: Disposition | None = None
    concession: str = ""
    removal: str = ""
    closed_on: date | None = None
    closed_by: str = ""

    def __post_init__(self) -> None:
        for name, value in (
            ("reference", self.reference),
            ("job", self.job),
            ("item", self.item),
            ("requirement", self.requirement),
            ("account", self.account),
            ("raised_by", self.raised_by),
        ):
            if not value.strip():
                raise ValidationError("a report needs this filled in", field=name)

    @property
    def is_open(self) -> bool:
        return self.closed_on is None

    def decide(self, disposition: Disposition, concession: str = "", removal: str = "") -> None:
        """Set the disposition, refusing one that arrives without its paperwork."""
        if not self.is_open:
            raise ConflictError("that report is closed", reference=self.reference)
        if disposition.needs_concession and not concession.strip():
            raise ConflictError(
                "that disposition needs a concession reference",
                reference=self.reference,
                disposition=str(disposition),
            )
        if disposition.needs_removal and not removal.strip():
            raise ConflictError(
                "a rejected item needs a removal reference", reference=self.reference
            )
        self.disposition = disposition
        self.concession = concession.strip()
        self.removal = removal.strip()

    def close(self, on: date, by: str) -> None:
        """Close it, refusing every way a close can be worthless."""
        if not self.is_open:
            raise ConflictError("that report is already closed", reference=self.reference)
        if self.disposition is None:
            raise ConflictError("nothing has been decided yet", reference=self.reference)
        if not by.strip():
            raise ValidationError("a close is signed")
        if by.strip() == self.raised_by:
            raise ConflictError(
                "a report is not closed by the person who raised it",
                reference=self.reference,
                badge=by,
            )
        if on < self.raised_on:
            raise ValidationError(
                "a report cannot close before it was raised", reference=self.reference
            )
        self.closed_on = on
        self.closed_by = by.strip()

    def days_open(self, on: date) -> int:
        end = self.closed_on or on
        return max(0, (end - self.raised_on).days)

    def is_overdue(self, on: date) -> bool:
        return self.is_open and self.days_open(on) > ESCALATE_AFTER_DAYS

    def escalates_on(self) -> date:
        return self.raised_on + timedelta(days=ESCALATE_AFTER_DAYS + 1)

    def describe(self) -> str:
        state = "open" if self.is_open else f"closed {self.closed_on} by {self.closed_by}"
        decided = str(self.disposition) if self.disposition else "undecided"
        return f"{self.reference} {self.job} {self.item}: {decided}, {state}"


@dataclass(slots=True)
class Register:
    """Every report, and the four lists somebody asks for on a Monday."""

    reports: dict[str, Report] = field(default_factory=dict)

    def raise_report(self, report: Report) -> None:
        if report.reference in self.reports:
            raise ConflictError(
                "that reference is already on the register", reference=report.reference
            )
        self.reports[report.reference] = report

    def get(self, reference: str) -> Report:
        found = self.reports.get(reference)
        if found is None:
            raise ConflictError("no such report", reference=reference)
        return found

    def __len__(self) -> int:
        return len(self.reports)

    def for_job(self, job: str) -> tuple[Report, ...]:
        found = [r for r in self.reports.values() if r.job == job]
        return tuple(sorted(found, key=lambda r: (r.raised_on, r.reference)))

    def open_reports(self) -> tuple[Report, ...]:
        found = [r for r in self.reports.values() if r.is_open]
        return tuple(sorted(found, key=lambda r: (r.raised_on, r.reference)))

    def overdue_on(self, on: date) -> tuple[Report, ...]:
        return tuple(r for r in self.open_reports() if r.is_overdue(on))

    def awaiting_a_decision(self) -> tuple[Report, ...]:
        return tuple(r for r in self.open_reports() if r.disposition is None)

    def by_disposition(self) -> dict[str, int]:
        counted: dict[str, int] = {}
        for report in self.reports.values():
            if report.disposition is not None:
                counted[str(report.disposition)] = counted.get(str(report.disposition), 0) + 1
        return counted

    def concessions_on(self, job: str) -> tuple[str, ...]:
        """Every concession reference the client has signed against one job."""
        found = {r.concession for r in self.for_job(job) if r.concession}
        return tuple(sorted(found))

    def blocks_closing(self, job: str) -> tuple[str, ...]:
        """Why this job cannot be signed off, in the words the coordinator uses."""
        reasons = []
        for report in self.for_job(job):
            if report.is_open:
                reasons.append(f"{report.reference} is still open")
            elif report.disposition is Disposition.REJECT and not report.removal:
                reasons.append(f"{report.reference} rejected an item with no removal reference")
        return tuple(reasons)
