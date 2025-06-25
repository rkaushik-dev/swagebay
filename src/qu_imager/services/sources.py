"""Registering sources, sending them out, and answering for them.

The rule that matters here is single occupancy. A source is either in the shop
or on exactly one job, and there is no state in between. A crew that takes a
source without booking it out and a coordinator who books it out to a second
job both produce the same corrupt record, which is a source in two places, and
the accountability report cannot tell those apart from a lost source.

So sending out is refused if the source is already out, bringing back is
refused if it is not, and both go through the same place. Nothing else writes
to the trip table.

Fitness is computed rather than stored. A source is fit on a given morning or
it is not, and the answer depends on the date, so storing it as a flag means
storing a flag that is wrong every day after the one it was written on.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.models.sources import DeviceRow, SightingRow, TripRow
from qu_imager.persistence.source_mapper import row_to_source, source_to_row
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.personnel import Person
from qu_imager.services.dispatch import require_clearance
from qu_imager.sources.sealed import SealedSource, SourceState


@dataclass(frozen=True, slots=True)
class Booking:
    """A source going out on a job."""

    source_serial: str
    device_serial: str
    crew_badge: str
    site: str
    went_out: date
    crew: tuple[Person, ...] = ()

    def __post_init__(self) -> None:
        for name, value in (
            ("crew_badge", self.crew_badge),
            ("site", self.site),
        ):
            if not value.strip():
                raise ValidationError("a booking needs this filled in", field=name)


@dataclass(frozen=True, slots=True)
class Fitness:
    """Whether a source can go out that morning, and why not."""

    serial: str
    on: date
    fit: bool
    reasons: tuple[str, ...]

    def describe(self) -> str:
        return "fit" if self.fit else "; ".join(self.reasons)


def register_source(work: UnitOfWork, source: SealedSource) -> None:
    """Put a new source on the inventory, refusing a serial already there."""
    if work.sources.find(source.serial) is not None:
        raise ConflictError("that source is already on the inventory", serial=source.serial)
    work.sources.add(source_to_row(source))
    work.flush()


def register_device(work: UnitOfWork, serial: str, model_key: str) -> None:
    if not serial.strip():
        raise ValidationError("a device has a serial number")
    if work.sources.find_device(serial) is not None:
        raise ConflictError("that device is already on the inventory", serial=serial)
    work.session.add(DeviceRow(serial=serial, model_key=model_key))
    work.flush()


def send_out(work: UnitOfWork, booking: Booking) -> None:
    """Book a source out to one job, refusing anything that is not cleared.

    The single occupancy check stays here because it is about the trip table
    and nothing else knows about it. Everything else the morning asks is in
    the dispatch gate, which is called rather than restated, so a rule added
    there lands here without anybody remembering to do it.
    """
    source = work.sources.get(booking.source_serial)
    device = work.sources.device(booking.device_serial)
    if work.sources.open_trip(booking.source_serial) is not None:
        raise ConflictError("that source is already out", serial=booking.source_serial)
    require_clearance(
        work,
        booking.device_serial,
        booking.source_serial,
        booking.went_out,
        booking.crew,
        booking.crew_badge,
    )
    work.session.add(
        TripRow(
            source_id=source.id,
            device_id=device.id,
            crew_badge=booking.crew_badge,
            site=booking.site,
            went_out=booking.went_out,
        )
    )
    work.flush()


def bring_back(work: UnitOfWork, serial: str, on: date, exposures: int = 0) -> None:
    """Close the open trip, refusing if there is not one."""
    if exposures < 0:
        raise ValidationError("an exposure count is not negative", exposures=exposures)
    trip = work.sources.open_trip(serial)
    if trip is None:
        raise ConflictError("that source is not out on a job", serial=serial)
    if on < trip.went_out:
        raise ValidationError("a source cannot come back before it went out", serial=serial)
    trip.came_back = on
    trip.exposures = exposures
    work.flush()


def record_sighting(work: UnitOfWork, serial: str, on: date, by: str, where: str) -> None:
    if not by.strip():
        raise ValidationError("an inventory sighting is signed")
    source = work.sources.get(serial)
    source.sightings.append(SightingRow(seen_on=on, seen_by=by, location=where))
    work.flush()


def fitness(work: UnitOfWork, serial: str, on: datetime) -> Fitness:
    """Whether the source can go out that morning."""
    row = work.sources.get(serial)
    source = row_to_source(row)
    reasons = source.reasons_not_to_use(on)
    return Fitness(serial=serial, on=on.date(), fit=not reasons, reasons=reasons)


def location(work: UnitOfWork, serial: str) -> str:
    trip = work.sources.open_trip(serial)
    return trip.site if trip is not None else "shop"


def retire(work: UnitOfWork, serial: str) -> None:
    """Take a source out of service. It has to be back in the shop first."""
    if work.sources.open_trip(serial) is not None:
        raise ConflictError("that source is out on a job", serial=serial)
    row = work.sources.get(serial)
    row.state = SourceState.AWAITING_DISPOSAL.value
    work.flush()
