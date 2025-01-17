"""Film retention, which is a legal obligation with a physical filing cabinet behind it.

Radiographs are kept and the period depends on what they are of. A pipeline
under a road is kept for the life of the line, which nobody can put a date on,
so those are marked as indefinite rather than being given a made up date. A
pressure vessel is the vessel's life plus a margin. Ordinary structural work is
five years.

The three cases are different in kind and not just in length, which is why the
retention is a small type rather than a number of days. A period that cannot be
expressed as a date has to be able to say so, and a system that forces one ends
up with radiographs quietly destroyed in 2049 because somebody typed twenty
five years in 2024.

Location is part of the record because a film that cannot be found has not been
retained. The box number, the cabinet and the site are all on it, and a film
marked as retained with no location is reported as a fault rather than as
stored.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ConflictError, ValidationError


class Retention(Enum):
    """How long a radiograph is kept, which is three different kinds of answer."""

    FIVE_YEARS = "five years"
    VESSEL_LIFE = "life of the vessel"
    INDEFINITE = "life of the line"

    @property
    def has_an_end_date(self) -> bool:
        return self is Retention.FIVE_YEARS

    @property
    def days(self) -> int | None:
        return 1826 if self is Retention.FIVE_YEARS else None

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Location:
    """Where the film physically is."""

    site: str
    cabinet: str
    box: str

    def __post_init__(self) -> None:
        for name, value in (("site", self.site), ("cabinet", self.cabinet), ("box", self.box)):
            if not value.strip():
                raise ValidationError("a location needs this filled in", field=name)

    def describe(self) -> str:
        return f"{self.site} cabinet {self.cabinet} box {self.box}"


@dataclass(frozen=True, slots=True)
class Filed:
    """One radiograph, filed."""

    radiograph: str
    job: str
    filed_on: date
    retention: Retention
    location: Location | None

    def __post_init__(self) -> None:
        if not self.radiograph.strip():
            raise ValidationError("a filed radiograph has a number")

    @property
    def destroy_after(self) -> date | None:
        days = self.retention.days
        return None if days is None else self.filed_on + timedelta(days=days)

    def is_due_on(self, day: date) -> bool:
        due = self.destroy_after
        return due is not None and day >= due

    def faults(self) -> tuple[str, ...]:
        if self.location is None:
            return (f"{self.radiograph} is marked retained with no location",)
        return ()


@dataclass(slots=True)
class Archive:
    """The filing cabinet, as a record."""

    filed: dict[str, Filed] = field(default_factory=dict)

    def file(self, entry: Filed) -> None:
        if entry.radiograph in self.filed:
            raise ConflictError("that radiograph is already filed", radiograph=entry.radiograph)
        self.filed[entry.radiograph] = entry

    def find(self, radiograph: str) -> Filed | None:
        return self.filed.get(radiograph)

    def for_job(self, job: str) -> tuple[Filed, ...]:
        return tuple(entry for _number, entry in sorted(self.filed.items()) if entry.job == job)

    def due_for_destruction(self, day: date) -> tuple[str, ...]:
        return tuple(number for number, entry in sorted(self.filed.items()) if entry.is_due_on(day))

    def kept_indefinitely(self) -> tuple[str, ...]:
        return tuple(
            number
            for number, entry in sorted(self.filed.items())
            if not entry.retention.has_an_end_date
        )

    def faults(self) -> tuple[str, ...]:
        out: list[str] = []
        for _number, entry in sorted(self.filed.items()):
            out.extend(entry.faults())
        return tuple(out)

    def missing_from(self, expected: tuple[str, ...]) -> tuple[str, ...]:
        """Radiographs a job says exist that are not in the cabinet."""
        return tuple(number for number in expected if number not in self.filed)
