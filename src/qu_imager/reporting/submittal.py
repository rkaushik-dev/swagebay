"""The submittal, which is the package plus the things that are not in it.

A report package describes the work. A submittal is what physically goes to the
client, and it contains several things the package does not: the procedure the
work was done to, the personnel certifications of everybody who signed
anything, the equipment calibration certificates, and the film itself.

Those four are the ones that come back. A client receiving a stack of
radiographs with no procedure revision attached cannot check the work against
anything, and a client who finds one certificate expired assumes the rest are
too. So the submittal is checked before it goes rather than after it comes
back, and the check is a list of what is enclosed against a list of what is
required.

Required is not a constant. A client specification says what it wants
enclosed, and the ones that ask for less are as real as the ones that ask for
more, so the requirement is an argument and the default is only a default.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.reporting.package import Package


class Enclosure(Enum):
    """One thing a submittal can contain."""

    REPORT = "radiographic report"
    RADIOGRAPHS = "the radiographs"
    PROCEDURE = "the procedure revision"
    CERTIFICATIONS = "personnel certifications"
    CALIBRATIONS = "equipment calibration certificates"
    WELD_MAP = "weld map"
    REPAIR_RECORD = "repair record"

    def __str__(self) -> str:
        return self.value


DEFAULT_REQUIRED = (
    Enclosure.REPORT,
    Enclosure.RADIOGRAPHS,
    Enclosure.PROCEDURE,
    Enclosure.CERTIFICATIONS,
    Enclosure.CALIBRATIONS,
)


@dataclass(frozen=True, slots=True)
class Submittal:
    """What is going to the client, and under what covering note."""

    job: str
    client: str
    prepared_on: date
    prepared_by: str
    enclosures: frozenset[Enclosure]
    revision: int = 0

    def __post_init__(self) -> None:
        if not self.prepared_by.strip():
            raise ValidationError("a submittal is signed")
        if self.revision < 0:
            raise ValidationError("a revision is not negative", revision=self.revision)

    def missing(self, required: tuple[Enclosure, ...] = DEFAULT_REQUIRED) -> tuple[Enclosure, ...]:
        return tuple(item for item in required if item not in self.enclosures)

    def extra(self, required: tuple[Enclosure, ...] = DEFAULT_REQUIRED) -> tuple[Enclosure, ...]:
        """What is enclosed that was not asked for, which is not a fault."""
        return tuple(sorted((self.enclosures - set(required)), key=lambda e: e.name))

    def is_ready(self, required: tuple[Enclosure, ...] = DEFAULT_REQUIRED) -> bool:
        return not self.missing(required)

    def covering_note(self) -> str:
        lines = [
            f"Job {self.job} for {self.client}",
            f"Prepared {self.prepared_on.isoformat()} by {self.prepared_by}",
        ]
        if self.revision:
            lines.append(f"Submittal revision {self.revision}")
        lines.append("Enclosed:")
        lines.extend(f"  {item}" for item in sorted(self.enclosures, key=lambda e: e.name))
        return "\n".join(lines)


@dataclass(slots=True)
class Readiness:
    """Everything standing between a package and the post."""

    package_faults: tuple[str, ...] = field(default_factory=tuple)
    missing_enclosures: tuple[Enclosure, ...] = field(default_factory=tuple)

    @property
    def is_ready(self) -> bool:
        return not self.package_faults and not self.missing_enclosures

    def describe(self) -> str:
        if self.is_ready:
            return "ready to go"
        parts = list(self.package_faults)
        parts.extend(f"no {item} enclosed" for item in self.missing_enclosures)
        return "; ".join(parts)


def check(
    package: Package,
    submittal: Submittal,
    required: tuple[Enclosure, ...] = DEFAULT_REQUIRED,
) -> Readiness:
    """The package and the parcel checked together, which is the only useful check."""
    return Readiness(
        package_faults=package.faults,
        missing_enclosures=submittal.missing(required),
    )


def revise(submittal: Submittal, enclosures: frozenset[Enclosure], on: date) -> Submittal:
    """A resubmittal is a new revision rather than an edit of the first."""
    return Submittal(
        job=submittal.job,
        client=submittal.client,
        prepared_on=on,
        prepared_by=submittal.prepared_by,
        enclosures=enclosures,
        revision=submittal.revision + 1,
    )
