"""Is this radiograph allowed to have an opinion about the weld.

Everything in this package up to here answers a piece of that question. This is
where the pieces are put together, and the assembly is the interesting part
rather than any one piece.

The rule that decides what the other rules measure is the medium. On film the
density gate runs and the noise gate is not applicable, and reporting a noise
failure on a piece of film is a category error, not a strict reading. On a
plate or a panel it is the other way round and the resolution gate joins in,
because a digital system can be sharp enough or not and film in this shop
always is.

The indicator gate runs on everything and it is the one that cannot be waived.
A radiograph whose essential hole is not visible is not a marginal radiograph,
it is not a radiograph, whatever its density says.

Every reason is collected. Stopping at the first failure produces a shooter who
fixes one thing, reshoots, and finds the second thing, which on a night shift
is two nights instead of one.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.imaging.density import DensityVerdict
from qu_imager.imaging.duplex import resolution_from_reading
from qu_imager.imaging.indicators import HoleIndicator, HoleSize, WireIndicator
from qu_imager.imaging.noise import NoiseMeasurement, required_normalised_snr
from qu_imager.imaging.system import ImagingSystem, Medium
from qu_imager.imaging.wire_selection import TechniqueClass
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class IndicatorEvidence:
    """What the reader could actually see of the indicator."""

    required_hole: HoleIndicator | None = None
    required_hole_size: HoleSize | None = None
    hole_visible: bool = False
    required_wire: WireIndicator | None = None
    finest_wire_visible: int | None = None

    def __post_init__(self) -> None:
        hole = self.required_hole is not None
        wire = self.required_wire is not None
        if hole == wire:
            raise ValidationError(
                "a radiograph is graded on a hole indicator or a wire one, not both"
            )
        if hole and self.required_hole_size is None:
            raise ValidationError("a hole indicator needs an essential hole")

    def satisfied(self) -> bool:
        if self.required_hole is not None:
            return self.hole_visible
        if self.finest_wire_visible is None:
            return False
        assert self.required_wire is not None
        return self.finest_wire_visible >= self.required_wire.number

    def describe_failure(self) -> str:
        if self.required_hole is not None:
            return (
                f"the {self.required_hole_size} hole in the "
                f"{self.required_hole.label()} plaque is not visible"
            )
        assert self.required_wire is not None
        seen = self.finest_wire_visible
        if seen is None:
            return f"no wire is visible, {self.required_wire.iso_label} is required"
        return f"the finest wire visible is W{seen}, {self.required_wire.iso_label} is required"


@dataclass(frozen=True, slots=True)
class Evidence:
    """Everything the reader wrote down about one radiograph."""

    system: ImagingSystem
    penetrated: Length
    technique_class: TechniqueClass
    indicator: IndicatorEvidence
    density: DensityVerdict | None = None
    noise: NoiseMeasurement | None = None
    duplex_reading: int | None = None
    required_resolution: Length | None = None
    notes: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True, slots=True)
class Admissibility:
    """The verdict, with every reason against it."""

    admissible: bool
    reasons: tuple[str, ...]
    gates_run: tuple[str, ...]

    def describe(self) -> str:
        if self.admissible:
            return "admissible"
        return "; ".join(self.reasons)


def assess(evidence: Evidence) -> Admissibility:
    """Run the gates the medium calls for, and collect every failure."""
    reasons: list[str] = []
    gates: list[str] = []

    gates.append("indicator")
    if not evidence.indicator.satisfied():
        reasons.append(evidence.indicator.describe_failure())

    if evidence.system.medium.graded_on_density:
        gates.append("density")
        if evidence.density is None:
            reasons.append("no density readings on record")
        elif not evidence.density.passed:
            reasons.extend(evidence.density.reasons)
    else:
        gates.append("normalised snr")
        floor = required_normalised_snr(evidence.penetrated, evidence.technique_class)
        if evidence.noise is None:
            reasons.append("no signal to noise measurement on record")
        elif evidence.noise.normalised < floor:
            reasons.append(
                f"normalised snr {evidence.noise.normalised:.1f} is under the floor of {floor}"
            )
        gates.append("basic spatial resolution")
        reasons.extend(_resolution_reasons(evidence))

    return Admissibility(admissible=not reasons, reasons=tuple(reasons), gates_run=tuple(gates))


def _resolution_reasons(evidence: Evidence) -> list[str]:
    required = evidence.required_resolution
    if required is None:
        return []
    if evidence.duplex_reading is None:
        return ["no duplex gauge reading on record"]
    measured = resolution_from_reading(evidence.duplex_reading)
    if measured > required:
        return [
            f"measured resolution {measured.format_mm(3)} is coarser than {required.format_mm(3)}"
        ]
    return []


def density_is_applicable(system: ImagingSystem) -> bool:
    return system.medium is Medium.FILM


def noise_floor_for(evidence: Evidence) -> Decimal:
    return Decimal(required_normalised_snr(evidence.penetrated, evidence.technique_class))
