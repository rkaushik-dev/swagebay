"""Source exchange, which is the day a device gets a different source in it.

Iridium 192 halves every seventy four days, so a source that went in at ninety
curies is doing forty five in the spring and the crew is standing in the rain
for twice as long as they were. About twice a year the shop swaps it. The old
source goes back to the manufacturer in a type B package and the new one comes
out of the one it arrived in.

The exchange is a single event that changes four things at once, which is why
it is written as one object rather than four calls. The device now holds a
different source. The old source is in transit and cannot be booked out. The
new source is in service and can. And there is a shipment number that ties the
two halves together, because the day somebody asks where a source went, the
answer has to be a document and not a recollection.

The rules here are the ones that have actually caught something.

A device holds one source. Loading a second without unloading the first is the
paperwork equivalent of two sources in one pig, and the record has to refuse it
even though the physical device would not fit them.

A source that failed its last wipe test does not go into a device. It goes into
a return package.

The device certificate decides what may be loaded, both the isotope and the
curie. That check already exists on the model and is called here rather than
copied.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, date, datetime, time

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.safety.inventory import DeviceUnit
from qu_imager.sources.sealed import SealedSource, SourceState


@dataclass(frozen=True, slots=True)
class Exchange:
    """One swap: what came out, what went in, and the paperwork behind it."""

    device_serial: str
    removed_serial: str | None
    installed_serial: str | None
    performed_on: date
    performed_by: str
    shipment: str
    notes: str = ""

    def __post_init__(self) -> None:
        if not self.performed_by.strip():
            raise ValidationError("an exchange is signed by the person who did it")
        if not self.shipment.strip():
            raise ValidationError("an exchange names the shipment it came on")
        if self.removed_serial is None and self.installed_serial is None:
            raise ValidationError("an exchange that moved nothing is not an exchange")
        if self.removed_serial is not None and self.removed_serial == self.installed_serial:
            raise ValidationError(
                "that is the same source going out and coming back in",
                serial=self.removed_serial,
            )

    @property
    def is_load(self) -> bool:
        return self.removed_serial is None

    @property
    def is_unload(self) -> bool:
        return self.installed_serial is None

    def describe(self) -> str:
        if self.is_load:
            return f"{self.installed_serial} into {self.device_serial} on {self.performed_on}"
        if self.is_unload:
            return f"{self.removed_serial} out of {self.device_serial} on {self.performed_on}"
        return (
            f"{self.removed_serial} out and {self.installed_serial} into "
            f"{self.device_serial} on {self.performed_on}"
        )


@dataclass(slots=True)
class Loading:
    """Which source is in which device, and every swap that got it there."""

    held: dict[str, str]
    history: list[Exchange]

    def __init__(self) -> None:
        self.held = {}
        self.history = []

    def source_in(self, device_serial: str) -> str | None:
        return self.held.get(device_serial)

    def device_holding(self, source_serial: str) -> str | None:
        for device, source in self.held.items():
            if source == source_serial:
                return device
        return None

    def loaded_devices(self) -> tuple[str, ...]:
        return tuple(sorted(self.held))

    def exchanges_for(self, device_serial: str) -> tuple[Exchange, ...]:
        found = [e for e in self.history if e.device_serial == device_serial]
        return tuple(sorted(found, key=lambda e: e.performed_on))

    def apply(
        self,
        exchange: Exchange,
        device: DeviceUnit,
        removed: SealedSource | None = None,
        installed: SealedSource | None = None,
    ) -> None:
        """Do the swap, or refuse it and change nothing."""
        self._check(exchange, device, removed, installed)
        if exchange.removed_serial is not None:
            del self.held[exchange.device_serial]
            if removed is not None:
                removed.state = SourceState.IN_TRANSIT
        if exchange.installed_serial is not None:
            self.held[exchange.device_serial] = exchange.installed_serial
            if installed is not None:
                installed.state = SourceState.IN_SERVICE
        self.history.append(exchange)

    def _check(
        self,
        exchange: Exchange,
        device: DeviceUnit,
        removed: SealedSource | None,
        installed: SealedSource | None,
    ) -> None:
        if device.serial != exchange.device_serial:
            raise ValidationError(
                "that exchange is against a different device",
                named=exchange.device_serial,
                given=device.serial,
            )
        holding = self.source_in(exchange.device_serial)
        if exchange.removed_serial is not None and holding != exchange.removed_serial:
            raise ConflictError(
                "that device is not holding the source the exchange takes out",
                device=exchange.device_serial,
                holding=holding or "nothing",
                named=exchange.removed_serial,
            )
        if exchange.removed_serial is None and holding is not None:
            raise ConflictError(
                "that device already holds a source",
                device=exchange.device_serial,
                holding=holding,
            )
        if exchange.installed_serial is not None:
            elsewhere = self.device_holding(exchange.installed_serial)
            if elsewhere is not None and elsewhere != exchange.device_serial:
                raise ConflictError(
                    "that source is already in another device",
                    source=exchange.installed_serial,
                    device=elsewhere,
                )
        if installed is not None:
            self._check_installed(exchange, device, installed)
        if removed is not None and removed.serial != exchange.removed_serial:
            raise ValidationError(
                "that is not the source the exchange takes out",
                named=exchange.removed_serial or "nothing",
                given=removed.serial,
            )

    def _check_installed(
        self, exchange: Exchange, device: DeviceUnit, installed: SealedSource
    ) -> None:
        if installed.serial != exchange.installed_serial:
            raise ValidationError(
                "that is not the source the exchange puts in",
                named=exchange.installed_serial or "nothing",
                given=installed.serial,
            )
        at = _instant(exchange.performed_on)
        if not installed.leak_test_current_at(at):
            raise ConflictError(
                "a source with no current wipe test goes into a package, not a device",
                source=installed.serial,
            )
        if installed.state is SourceState.AWAITING_DISPOSAL:
            raise ConflictError("that source is awaiting disposal", source=installed.serial)
        device.model.check_load(installed.isotope, installed.activity_at(at))


def _instant(day: date) -> datetime:
    """Midnight universal time on an exchange day.

    An exchange is recorded as a date because that is what the paperwork
    carries, and decay wants an instant. Midnight utc is the one reading of a
    date that does not depend on where the shop is, and the error it puts on a
    seventy four day half life is under a tenth of a percent.
    """
    return datetime.combine(day, time.min, tzinfo=UTC)


def activity_lost(source: SealedSource, went_in: date, came_out: date) -> Activity:
    """How much of a source's activity a device used up over its time in."""
    if came_out < went_in:
        raise ValidationError("a source cannot come out before it went in")
    start = _instant(went_in)
    end = _instant(came_out)
    return source.activity_at(start) - source.activity_at(end)
