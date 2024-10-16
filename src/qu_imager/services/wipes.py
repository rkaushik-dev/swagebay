"""Leak tests, and the history sheet a regulator asks for.

A wipe test is two things at once and the software has to do both. It is a
measurement, so it goes on the record with its result and its date. And it is a
gate, because a failing wipe takes the source out of service immediately and
the crew has to be told before they load it into a truck.

The immediate part is the reason this is a service rather than a repository
write. Recording a failure changes the state of the source in the same
transaction, so there is no window in which the shop holds a failed wipe and a
source still marked in service.

The history sheet is ordered oldest first, which is the opposite of most
listings here and is deliberate: a regulator reading a source history reads it
forwards, from receipt to disposal, and a sheet that starts at the end is a
sheet somebody has to reverse by hand.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.persistence.models.sources import LeakTestRow
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.sources.sealed import LEAK_TEST_INTERVAL_DAYS, LeakTest, SourceState


@dataclass(frozen=True, slots=True)
class WipeResult:
    """What recording a wipe did."""

    serial: str
    passed: bool
    expires_on: date
    source_state: str

    def describe(self) -> str:
        if self.passed:
            return f"{self.serial} passed, current until {self.expires_on.isoformat()}"
        return f"{self.serial} failed, source is now {self.source_state}"


@dataclass(frozen=True, slots=True)
class HistoryEntry:
    """One line of a source history sheet."""

    at: datetime
    what: str
    detail: str


def record_wipe(
    work: UnitOfWork,
    serial: str,
    performed_at: datetime,
    becquerels_removed: Decimal,
    technician: str,
) -> WipeResult:
    """Store a wipe, and take the source out of service if it failed."""
    if not technician.strip():
        raise ValidationError("a wipe test is signed")
    if becquerels_removed < 0:
        raise ValidationError("a wipe result is not negative", removed=str(becquerels_removed))
    row = work.sources.get(serial)
    test = LeakTest(performed_at, becquerels_removed, technician)
    row.leak_tests.append(
        LeakTestRow(
            performed_at=performed_at,
            becquerels_removed=becquerels_removed,
            technician=technician,
        )
    )
    if not test.passed:
        row.state = SourceState.AWAITING_DISPOSAL.value
    work.flush()
    return WipeResult(
        serial=serial,
        passed=test.passed,
        expires_on=(performed_at + timedelta(days=LEAK_TEST_INTERVAL_DAYS)).date(),
        source_state=row.state,
    )


def wipe_due_on(work: UnitOfWork, serial: str) -> date | None:
    """When the current wipe runs out, or nothing if there has never been one."""
    row = work.sources.get(serial)
    passing = [t for t in row.leak_tests if t.becquerels_removed < LeakTest.LIMIT_BQ]
    if not passing:
        return None
    latest = max(passing, key=lambda t: t.performed_at)
    return (latest.performed_at + timedelta(days=LEAK_TEST_INTERVAL_DAYS)).date()


def wipes_overdue(work: UnitOfWork, on: date) -> tuple[str, ...]:
    """Every source in service whose wipe has run out by that day."""
    out: list[str] = []
    for row in work.sources.in_service():
        due = wipe_due_on(work, row.serial)
        if due is None or due <= on:
            out.append(row.serial)
    return tuple(out)


def history(work: UnitOfWork, serial: str) -> tuple[HistoryEntry, ...]:
    """Everything that has happened to one source, oldest first."""
    row = work.sources.get(serial)
    entries: list[HistoryEntry] = [
        HistoryEntry(
            at=row.assay_at,
            what="assay",
            detail=f"{row.assay_activity.format_curie()} on the certificate",
        )
    ]
    for test in row.leak_tests:
        verdict = "passed" if test.becquerels_removed < LeakTest.LIMIT_BQ else "failed"
        entries.append(
            HistoryEntry(
                at=test.performed_at,
                what="leak test",
                detail=f"{verdict}, {test.becquerels_removed} Bq removed by {test.technician}",
            )
        )
    for trip in row.trips:
        entries.append(
            HistoryEntry(
                at=datetime.combine(trip.went_out, datetime.min.time()),
                what="sent out",
                detail=f"to {trip.site} with {trip.crew_badge}",
            )
        )
        if trip.came_back is not None:
            entries.append(
                HistoryEntry(
                    at=datetime.combine(trip.came_back, datetime.min.time()),
                    what="came back",
                    detail=f"{trip.exposures} exposures",
                )
            )
    for sighting in row.sightings:
        entries.append(
            HistoryEntry(
                at=datetime.combine(sighting.seen_on, datetime.min.time()),
                what="sighted",
                detail=f"at {sighting.location} by {sighting.seen_by}",
            )
        )
    return tuple(sorted(entries, key=lambda e: (e.at, e.what)))
