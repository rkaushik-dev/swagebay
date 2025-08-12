"""Checks that look for records which cannot be true at the same time.

Every rule in this package is enforced on the way in. That is the right place
for it and it is not enough, because a database outlives the code that wrote
it. Rows get in through a migration, through an import, through somebody with a
console, and through a version of this package that did not have the rule yet.
The constraint added on a Tuesday does not go back and check Monday.

So this reads what is there and says what cannot be true. It is not a repair
tool and it does not offer to fix anything, because every one of these has more
than one possible cause and picking one automatically is how a shop loses a
radiograph rather than finds out about it.

Each check is separate and each returns its own findings, so a shop with one
bad row does not get a single line saying the database is unhealthy.
"""

from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import func, select

from qu_imager.identity import JobNumber
from qu_imager.persistence.models.counters import CounterRow
from qu_imager.persistence.models.maintenance import InspectionRow
from qu_imager.persistence.models.nonconformance import NonconformanceRow
from qu_imager.persistence.models.sources import DeviceRow, SourceRow, TripRow
from qu_imager.persistence.models.store import StoredImageRow
from qu_imager.persistence.models.work import JobRow, RadiographRow, WeldRow
from qu_imager.persistence.unit_of_work import UnitOfWork


@dataclass(frozen=True, slots=True)
class Finding:
    """One thing that cannot be true, and what it is about."""

    check: str
    subject: str
    account: str

    def describe(self) -> str:
        return f"{self.check}: {self.subject} {self.account}"


@dataclass(frozen=True, slots=True)
class Report:
    """Everything the checks found."""

    findings: tuple[Finding, ...]
    checks_run: tuple[str, ...]

    @property
    def is_healthy(self) -> bool:
        return not self.findings

    def by_check(self) -> dict[str, tuple[Finding, ...]]:
        grouped: dict[str, list[Finding]] = {}
        for finding in self.findings:
            grouped.setdefault(finding.check, []).append(finding)
        return {name: tuple(found) for name, found in grouped.items()}

    def describe(self) -> str:
        if self.is_healthy:
            return f"{len(self.checks_run)} checks, nothing found"
        return f"{len(self.checks_run)} checks, {len(self.findings)} findings"


def double_bookings(work: UnitOfWork) -> tuple[Finding, ...]:
    """A source cannot be out on two jobs, and the trip table cannot say so."""
    rows = work.session.execute(
        select(SourceRow.serial, func.count(TripRow.id))
        .join(TripRow, TripRow.source_id == SourceRow.id)
        .where(TripRow.came_back.is_(None))
        .group_by(SourceRow.serial)
        .having(func.count(TripRow.id) > 1)
    ).all()
    return tuple(
        Finding("double booking", serial, f"is out on {count} open trips") for serial, count in rows
    )


def numbering_gaps(work: UnitOfWork) -> tuple[Finding, ...]:
    """A radiograph number above the counter, or a counter below what was issued.

    Either one means a number was handed out by something other than the
    counter, which is the failure the counter design exists to prevent and the
    only one that cannot be seen by looking at a single row.
    """
    findings: list[Finding] = []
    counters = {
        (row.name, row.scope): row.next_value
        for row in work.session.scalars(select(CounterRow)).all()
    }
    for job in work.session.scalars(select(JobRow)).all():
        number = JobNumber(job.year, job.serial)
        issued = work.session.scalars(
            select(RadiographRow.number)
            .join(WeldRow, WeldRow.id == RadiographRow.weld_id)
            .where(WeldRow.job_id == job.id)
        ).all()
        if not issued:
            continue
        highest = max(int(text.rsplit("R", 1)[-1]) for text in issued)
        held = counters.get(("radiograph", str(number)))
        if held is None:
            findings.append(
                Finding("numbering", str(number), f"has {len(issued)} films and no counter")
            )
        elif highest >= held:
            findings.append(
                Finding("numbering", str(number), f"issued R{highest:04d} past a counter at {held}")
            )
    return tuple(findings)


def orphaned_images(work: UnitOfWork) -> tuple[Finding, ...]:
    """A file in the store against an exposure that is not there any more."""
    rows = work.session.execute(
        select(StoredImageRow.filename)
        .outerjoin(RadiographRow, RadiographRow.id == StoredImageRow.radiograph_id)
        .where(RadiographRow.id.is_(None))
    ).all()
    return tuple(Finding("orphaned image", name, "has no radiograph behind it") for (name,) in rows)


def unknown_devices(work: UnitOfWork) -> tuple[Finding, ...]:
    """An inspection against a device nobody put on the inventory."""
    known = {row.serial for row in work.session.scalars(select(DeviceRow)).all()}
    serials = work.session.scalars(select(InspectionRow.device_serial).distinct()).all()
    return tuple(
        Finding("unknown device", serial, "has inspections and is not on the inventory")
        for serial in sorted(set(serials) - known)
    )


def self_closed_reports(work: UnitOfWork) -> tuple[Finding, ...]:
    """A report closed by the person who raised it, from before that was refused."""
    rows = work.session.scalars(
        select(NonconformanceRow).where(
            NonconformanceRow.closed_by.is_not(None),
            NonconformanceRow.closed_by == NonconformanceRow.raised_by,
        )
    ).all()
    return tuple(
        Finding("self closed", row.reference, f"was closed by {row.closed_by} who raised it")
        for row in rows
    )


def undecided_closures(work: UnitOfWork) -> tuple[Finding, ...]:
    """A report closed without a disposition, which decides nothing."""
    rows = work.session.scalars(
        select(NonconformanceRow).where(
            NonconformanceRow.closed_on.is_not(None),
            NonconformanceRow.disposition.is_(None),
        )
    ).all()
    return tuple(
        Finding("undecided closure", row.reference, "is closed with nothing decided")
        for row in rows
    )


CHECKS = (
    ("double booking", double_bookings),
    ("numbering", numbering_gaps),
    ("orphaned image", orphaned_images),
    ("unknown device", unknown_devices),
    ("self closed", self_closed_reports),
    ("undecided closure", undecided_closures),
)


def examine(work: UnitOfWork, only: tuple[str, ...] = ()) -> Report:
    """Run every check, or the named ones, and gather what they found."""
    chosen = [(name, check) for name, check in CHECKS if not only or name in only]
    findings: list[Finding] = []
    for _, check in chosen:
        findings.extend(check(work))
    return Report(findings=tuple(findings), checks_run=tuple(name for name, _ in chosen))


def check_names() -> tuple[str, ...]:
    return tuple(name for name, _ in CHECKS)
