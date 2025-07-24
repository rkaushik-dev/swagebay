"""The morning gate: can this crew take this camera to that site today.

Every part of this answer already existed and was in a different place. The
source knows whether its wipe test is current. The maintenance register knows
whether the quarterly ran out and whether anybody looked at the drive cable
that morning. The person knows whether their card and their eye exam are still
good. Nobody was asking all three in one go, so the yard asked them one at a
time and stopped at the first yes.

The gate returns every reason rather than the first, because a crew that fixes
one and comes back is a crew that makes two trips to the shop. The order is
the order somebody can act on: the ones that are somebody driving to the office
with a form, then the ones that are a phone call, then the ones that are a week.

It refuses to guess. A device nobody has ever inspected is not treated as
inspected, and a source that is not on the inventory is not treated as fit. An
absent record is a refusal here, which is the opposite of how most of this
package treats a missing optional, and it is the right way round for the one
question whose wrong answer puts somebody in a field beside a live source.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from qu_imager.clock import start_of
from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.persistence.models.sources import SourceRow
from qu_imager.persistence.source_mapper import row_to_source
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.personnel import Person
from qu_imager.safety.maintenance import MaintenanceRegister


@dataclass(frozen=True, slots=True)
class Yard:
    """Everything the gate needs about the whole shop, read once.

    Asking about one load is three small queries and that is fine. Asking about
    every pairing in the yard through the same path was three per pairing, so
    six cameras and four sources cost seventy two round trips to read ten rows.
    This is those ten rows.
    """

    register: MaintenanceRegister
    devices: frozenset[str]
    sources: dict[str, SourceRow]
    out: frozenset[str]

    @classmethod
    def load(cls, work: UnitOfWork) -> Yard:
        devices = work.sources.devices()
        serials = tuple(row.serial for row in devices)
        return cls(
            register=work.maintenance.register_for(serials),
            devices=frozenset(serials),
            sources={row.serial: row for row in work.sources.in_service()},
            out=work.sources.open_trip_serials(),
        )


@dataclass(frozen=True, slots=True)
class Clearance:
    """Whether the truck moves, and everything stopping it if it does not."""

    device_serial: str
    source_serial: str
    day: date
    source_reasons: tuple[str, ...]
    device_reasons: tuple[str, ...]
    crew_reasons: tuple[str, ...]

    @property
    def reasons(self) -> tuple[str, ...]:
        return self.source_reasons + self.device_reasons + self.crew_reasons

    @property
    def cleared(self) -> bool:
        return not self.reasons

    def describe(self) -> str:
        if self.cleared:
            return f"{self.device_serial} with {self.source_serial} is clear for {self.day}"
        return "; ".join(self.reasons)


def clearance(
    work: UnitOfWork,
    device_serial: str,
    source_serial: str,
    day: date,
    crew: tuple[Person, ...] = (),
    lead_badge: str = "",
    yard: Yard | None = None,
) -> Clearance:
    """Ask all three questions at once and hand back every answer.

    The yard can be handed in already read. Asking about one load without it
    is three small queries and that is right. Asking about every load in the
    yard through the same path was three per pairing.
    """
    source_reasons = _source_reasons(work, source_serial, day, yard)
    device_reasons = _device_reasons(work, device_serial, day, yard)
    crew_reasons = _crew_reasons(crew, day, lead_badge)
    return Clearance(
        device_serial=device_serial,
        source_serial=source_serial,
        day=day,
        source_reasons=source_reasons,
        device_reasons=device_reasons,
        crew_reasons=crew_reasons,
    )


def _source_reasons(
    work: UnitOfWork, serial: str, day: date, yard: Yard | None = None
) -> tuple[str, ...]:
    row = yard.sources.get(serial) if yard is not None else work.sources.find(serial)
    if row is None:
        return (f"source {serial} is not on the inventory",)
    at = start_of(day)
    reasons = row_to_source(row).reasons_not_to_use(at)
    is_out = serial in yard.out if yard is not None else work.sources.open_trip(serial)
    if is_out:
        reasons = (*reasons, f"source {serial} is already out")
    return tuple(f"source {serial}: {reason}" for reason in reasons)


def _device_reasons(
    work: UnitOfWork, serial: str, day: date, yard: Yard | None = None
) -> tuple[str, ...]:
    known = serial in yard.devices if yard is not None else work.sources.find_device(serial)
    if not known:
        return (f"device {serial} is not on the inventory",)
    register = yard.register if yard is not None else work.maintenance.register_for((serial,))
    return tuple(
        f"device {serial}: {reason}" for reason in register.reasons_not_to_use(serial, day)
    )


def _crew_reasons(crew: tuple[Person, ...], day: date, lead_badge: str) -> tuple[str, ...]:
    if not crew:
        # A booking that carries only a badge is still a booking with somebody
        # on it. The credentials cannot be checked from a badge alone, and
        # pretending otherwise would be a check that always passes.
        return () if lead_badge.strip() else ("nobody is named on the booking",)
    reasons = []
    for person in crew:
        lapsed = person.lapsed_on(day)
        if lapsed:
            reasons.append(f"{person.badge}: {', '.join(lapsed)}")
    return tuple(reasons)


def require_clearance(
    work: UnitOfWork,
    device_serial: str,
    source_serial: str,
    day: date,
    crew: tuple[Person, ...] = (),
    lead_badge: str = "",
) -> Clearance:
    """The gate as a refusal, for the callers that cannot carry on without it."""
    found = clearance(work, device_serial, source_serial, day, crew, lead_badge)
    if not found.cleared:
        raise ConflictError(
            "that load is not cleared to leave the shop",
            device=device_serial,
            source=source_serial,
            reasons="; ".join(found.reasons),
        )
    return found


def cleared_devices(
    work: UnitOfWork, source_serial: str, day: date, crew: tuple[Person, ...] = ()
) -> tuple[str, ...]:
    """Which cameras this source could go out in today."""
    devices = work.sources.devices()
    if not devices:
        raise NotFoundError("device", "any")
    yard = Yard.load(work)
    found = [
        row.serial
        for row in devices
        if clearance(work, row.serial, source_serial, day, crew, "listing", yard).cleared
    ]
    return tuple(sorted(found))
