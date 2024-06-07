"""Where every source is, and who had it.

The regulator does not much care how good the radiographs are. It cares that
the licensee can say, at any moment and without looking anything up, where each
sealed source is and who is responsible for it. That obligation is continuous
and it does not pause when a crew is driving between sites.

Two records carry it. The utilization log says which source went out in which
device, with which crew, to which site, on which day, and when it came back. It
is filled in at the time, not reconstructed. The quarterly inventory is a
physical check: somebody lays eyes on the source, or on the device containing
it, and signs.

A source that is out on a job at inventory time is not missing, it is out on a
job, and the log says where. A source with no open log entry and no inventory
sighting inside the quarter is missing, and that is a phone call to the
regulator inside twenty four hours.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.sources.device import DeviceModel
from qu_imager.sources.sealed import SealedSource

INVENTORY_INTERVAL = timedelta(days=92)
"""A quarter, which the licence condition writes as three months."""


@dataclass(frozen=True, slots=True)
class DeviceUnit:
    """One physical exposure device the shop owns."""

    serial: str
    model: DeviceModel

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise ValidationError("a device has a serial number")

    def __str__(self) -> str:
        return f"{self.model.name} {self.serial}"


@dataclass(frozen=True, slots=True)
class Sighting:
    """Somebody laid eyes on a source on a day and signed for it."""

    source_serial: str
    seen_on: date
    seen_by: str
    location: str

    def __post_init__(self) -> None:
        if not self.seen_by.strip():
            raise ValidationError("an inventory sighting is signed")


@dataclass(slots=True)
class Trip:
    """One source, out in one device, with one crew, to one site."""

    source_serial: str
    device_serial: str
    crew_badge: str
    site: str
    went_out: date
    came_back: date | None = None
    exposures: int = 0

    def __post_init__(self) -> None:
        if self.came_back is not None and self.came_back < self.went_out:
            raise ValidationError(
                "a source cannot come back before it went out", source=self.source_serial
            )

    @property
    def is_open(self) -> bool:
        return self.came_back is None

    def covers(self, day: date) -> bool:
        if day < self.went_out:
            return False
        return self.came_back is None or day <= self.came_back


@dataclass(slots=True)
class Inventory:
    """Every source the shop holds, plus the two records that account for them."""

    sources: dict[str, SealedSource] = field(default_factory=dict)
    devices: dict[str, DeviceUnit] = field(default_factory=dict)
    trips: list[Trip] = field(default_factory=list)
    sightings: list[Sighting] = field(default_factory=list)

    def add_source(self, source: SealedSource) -> None:
        if source.serial in self.sources:
            raise ConflictError("that source is already on the inventory", serial=source.serial)
        self.sources[source.serial] = source

    def add_device(self, unit: DeviceUnit) -> None:
        if unit.serial in self.devices:
            raise ConflictError("that device is already on the inventory", serial=unit.serial)
        self.devices[unit.serial] = unit

    def send_out(self, trip: Trip) -> None:
        if trip.source_serial not in self.sources:
            raise ValidationError("that source is not on the inventory", serial=trip.source_serial)
        if trip.device_serial not in self.devices:
            raise ValidationError("that device is not on the inventory", serial=trip.device_serial)
        if self.open_trip(trip.source_serial) is not None:
            raise ConflictError("that source is already out on a job", serial=trip.source_serial)
        self.trips.append(trip)

    def bring_back(self, source_serial: str, on: date) -> None:
        trip = self.open_trip(source_serial)
        if trip is None:
            raise ConflictError("that source is not out on a job", serial=source_serial)
        if on < trip.went_out:
            raise ValidationError("a source cannot come back before it went out")
        trip.came_back = on

    def open_trip(self, source_serial: str) -> Trip | None:
        for trip in self.trips:
            if trip.source_serial == source_serial and trip.is_open:
                return trip
        return None

    def location_of(self, source_serial: str, day: date) -> str:
        trip = next(
            (t for t in self.trips if t.source_serial == source_serial and t.covers(day)), None
        )
        return trip.site if trip is not None else "shop"

    def last_sighting(self, source_serial: str) -> Sighting | None:
        seen = [s for s in self.sightings if s.source_serial == source_serial]
        return max(seen, key=lambda s: s.seen_on) if seen else None

    def unaccounted_on(self, day: date) -> tuple[str, ...]:
        """Sources with neither an open job nor a sighting inside the quarter."""
        out: list[str] = []
        for serial in sorted(self.sources):
            if self.open_trip(serial) is not None:
                continue
            seen = self.last_sighting(serial)
            if seen is None or day - seen.seen_on > INVENTORY_INTERVAL:
                out.append(serial)
        return tuple(out)

    def exposures_by(self, crew_badge: str) -> int:
        return sum(t.exposures for t in self.trips if t.crew_badge == crew_badge)
