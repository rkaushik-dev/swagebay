"""The utilisation log, which is the record an inspector asks for first.

The licence condition is short and it is not negotiable. For every exposure
device, for every day it was used, the log names the date, the device, the
source that was in it, where the work was, and every radiographer who operated
it. Three years of them, kept where they can be produced.

It reads like bookkeeping and it is not. It is the only record that ties a
person to a source on a day, so it is what a dose reconstruction is built from
when a badge comes back high, and it is what proves a device was not out when
somebody says it was. A log with a gap in it is worse than no log, because the
gap is the day everyone will ask about.

Two things this refuses.

A use with nobody on it. A device does not operate itself, and a log entry with
no badge is a day nobody can be asked about.

A use naming a source the device was not holding. That is caught here rather
than left to the reader, because the loading record already knows and the two
disagreeing is exactly the kind of thing that surfaces two years later in front
of somebody official.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.safety.inventory import Trip
from qu_imager.sources.exchange import Loading

RETENTION_YEARS = 3
RETENTION_DAYS = RETENTION_YEARS * 365


@dataclass(frozen=True, slots=True)
class Use:
    """One device, on one day, at one place, with the crew that ran it."""

    used_on: date
    device_serial: str
    source_serial: str
    site: str
    radiographers: tuple[str, ...]
    exposures: int = 0

    def __post_init__(self) -> None:
        if not self.device_serial.strip() or not self.source_serial.strip():
            raise ValidationError("a use names the device and the source in it")
        if not self.site.strip():
            raise ValidationError("a use names where the work was")
        if not self.radiographers:
            raise ValidationError("a device does not operate itself", device=self.device_serial)
        if any(not badge.strip() for badge in self.radiographers):
            raise ValidationError("a blank badge is not a radiographer")
        if len(set(self.radiographers)) != len(self.radiographers):
            raise ValidationError(
                "the same badge is on that entry twice", device=self.device_serial
            )
        if self.exposures < 0:
            raise ValidationError("exposures do not go backwards", value=str(self.exposures))

    @property
    def was_a_mobilisation(self) -> bool:
        """Out and back with nothing shot, which still has to be logged."""
        return self.exposures == 0

    def destroy_after(self) -> date:
        return self.used_on + timedelta(days=RETENTION_DAYS)

    def describe(self) -> str:
        crew = ", ".join(sorted(self.radiographers))
        return (
            f"{self.used_on} {self.device_serial} with {self.source_serial} "
            f"at {self.site}, {self.exposures} exposures, {crew}"
        )


@dataclass(slots=True)
class UtilisationLog:
    """Every use, and the questions the log gets asked."""

    uses: list[Use] = field(default_factory=list)

    def record(self, use: Use, loading: Loading | None = None) -> None:
        """Add a use, refusing one that contradicts the loading record."""
        if loading is not None:
            holding = loading.source_in(use.device_serial)
            if holding is not None and holding != use.source_serial:
                raise ConflictError(
                    "that device was not holding that source",
                    device=use.device_serial,
                    holding=holding,
                    claimed=use.source_serial,
                )
        self.uses.append(use)

    def for_device(self, serial: str, since: date, until: date) -> tuple[Use, ...]:
        found = [u for u in self.uses if u.device_serial == serial and since <= u.used_on <= until]
        return tuple(sorted(found, key=lambda u: u.used_on))

    def for_source(self, serial: str, since: date, until: date) -> tuple[Use, ...]:
        found = [u for u in self.uses if u.source_serial == serial and since <= u.used_on <= until]
        return tuple(sorted(found, key=lambda u: u.used_on))

    def for_radiographer(self, badge: str, since: date, until: date) -> tuple[Use, ...]:
        found = [u for u in self.uses if badge in u.radiographers and since <= u.used_on <= until]
        return tuple(sorted(found, key=lambda u: u.used_on))

    def days_used(self, serial: str, since: date, until: date) -> int:
        return len({u.used_on for u in self.for_device(serial, since, until)})

    def exposures_by_device(self, since: date, until: date) -> dict[str, int]:
        counted: dict[str, int] = {}
        for use in self.uses:
            if since <= use.used_on <= until:
                counted[use.device_serial] = counted.get(use.device_serial, 0) + use.exposures
        return counted

    def sites_worked(self, badge: str, since: date, until: date) -> tuple[str, ...]:
        return tuple(sorted({u.site for u in self.for_radiographer(badge, since, until)}))

    def unlogged_days(self, trips: list[Trip]) -> tuple[tuple[str, date], ...]:
        """Days a device was out on a trip with nothing in the log for it.

        This is the check the log exists for. A trip says the device left the
        shop; the log says what it did. A day with a trip and no entry is a day
        nobody wrote down, and the answer three years later is always that
        somebody meant to.
        """
        logged = {(u.device_serial, u.used_on) for u in self.uses}
        missing = []
        for trip in trips:
            last = trip.came_back or trip.went_out
            day = trip.went_out
            while day <= last:
                if (trip.device_serial, day) not in logged:
                    missing.append((trip.device_serial, day))
                day += timedelta(days=1)
        return tuple(sorted(set(missing)))

    def due_for_destruction(self, on: date) -> tuple[Use, ...]:
        found = [u for u in self.uses if u.destroy_after() <= on]
        return tuple(sorted(found, key=lambda u: u.used_on))

    def entries_after(self, on: date) -> tuple[Use, ...]:
        found = [u for u in self.uses if u.used_on > on]
        return tuple(sorted(found, key=lambda u: u.used_on))
