"""How much of a circumference one exposure can honestly carry.

The code minimum is three exposures for a double wall single image shot and
that is a floor, not an answer. Whether three is enough depends on how far back
the source is, because the beam only crosses the wall square on at the middle
of the film. Everywhere else it crosses at an angle and the path through the
steel is longer, so the image is darker, the contrast is worse, and past some
angle the weld at the edge of the film is not being examined at all.

The path through the wall goes as one over the cosine of the angle between the
ray and the wall normal. That angle is worked out here from the actual geometry
rather than from a rule of thumb: source at a known distance from a pipe of
known radius, and a point on the weld a known angle round from the beam axis.

The office allows the path at the edge of the diagnostic length to run ten
percent longer than the path at the middle. That number is a policy and it is
an argument the shop has had with clients more than once, so it is an argument
here rather than a constant.

Trigonometry is done in floating point. It is the one place in this package
where that is the right call: the inputs are tape measurements good to a
millimetre, the output is a count of exposures, and no amount of decimal
precision changes an answer that gets rounded up to a whole film.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from decimal import ROUND_CEILING, Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry.joint import Joint
from qu_imager.quantities.length import Length

DEFAULT_PATH_ALLOWANCE = Decimal("1.10")
"""The path at the edge of the film may run ten percent over the middle."""

STANDOFF_CEILING = Length.from_m(5)
"""Past this the guide tube will not reach and the exposure is absurd."""


@dataclass(frozen=True, slots=True)
class CoveragePlan:
    """How many exposures, how far apart, and how much each really carries."""

    exposures: int
    usable_half_arc_degrees: Decimal
    separation_degrees: Decimal
    overlap_degrees: Decimal

    @property
    def has_overlap(self) -> bool:
        return self.overlap_degrees > 0

    def describe(self) -> str:
        return (
            f"{self.exposures} exposures {self.separation_degrees:.1f} degrees apart, "
            f"each carrying {self.usable_half_arc_degrees * 2:.1f} degrees"
        )


def incidence_angle(radius: Length, source_to_surface: Length, phi_degrees: float) -> float:
    """The angle between the ray and the wall normal, in degrees.

    Zero at the middle of the film where the beam is square on, and it opens up
    as the point of interest moves round the pipe away from the beam axis.
    """
    if radius.um <= 0:
        raise ValidationError("a pipe has a radius")
    if source_to_surface.um <= 0:
        raise ValidationError("the source stands off the pipe")
    r = radius.um
    d = r + source_to_surface.um
    phi = math.radians(phi_degrees)
    chord = math.sqrt(d * d + r * r - 2 * d * r * math.cos(phi))
    if chord == 0:
        return 0.0
    cosine = (r * r + chord * chord - d * d) / (2 * r * chord)
    cosine = max(-1.0, min(1.0, cosine))
    return 180.0 - math.degrees(math.acos(cosine))


def path_factor(radius: Length, source_to_surface: Length, phi_degrees: float) -> Decimal:
    """How much longer the path through the wall is than at the middle."""
    angle = incidence_angle(radius, source_to_surface, phi_degrees)
    cosine = math.cos(math.radians(angle))
    if cosine <= 0:
        raise TechniqueError(
            "the beam is grazing the wall at that angle",
            phi_degrees=phi_degrees,
            incidence_degrees=round(angle, 2),
        )
    return Decimal(str(round(1.0 / cosine, 6)))


def usable_half_arc(
    joint: Joint,
    source_to_surface: Length,
    allowance: Decimal = DEFAULT_PATH_ALLOWANCE,
) -> Decimal:
    """The half arc either side of the beam axis that stays inside the allowance."""
    if allowance <= 1:
        raise ValidationError("an allowance over the middle path is greater than one")
    radius = joint.od / 2
    low, high = 0.0, 180.0
    for _ in range(60):
        middle = (low + high) / 2
        try:
            factor = path_factor(radius, source_to_surface, middle)
        except TechniqueError:
            high = middle
            continue
        if factor <= allowance:
            low = middle
        else:
            high = middle
    return Decimal(str(round(low, 3)))


def plan_coverage(
    joint: Joint,
    source_to_surface: Length,
    minimum_exposures: int,
    allowance: Decimal = DEFAULT_PATH_ALLOWANCE,
) -> CoveragePlan:
    """The exposure count geometry demands, never below the code minimum."""
    if minimum_exposures <= 0:
        raise ValidationError("the code minimum is at least one exposure")
    half = usable_half_arc(joint, source_to_surface, allowance)
    if half <= 0:
        raise TechniqueError(
            "no part of the weld is inside the path allowance at that distance",
            standoff_mm=str(source_to_surface.mm),
        )
    per_exposure = half * 2
    needed = int((Decimal(360) / per_exposure).to_integral_value(rounding=ROUND_CEILING))
    exposures = max(needed, minimum_exposures)
    separation = Decimal(360) / exposures
    return CoveragePlan(
        exposures=exposures,
        usable_half_arc_degrees=half,
        separation_degrees=separation,
        overlap_degrees=per_exposure - separation,
    )


def standoff_for_exposures(
    joint: Joint,
    exposures: int,
    allowance: Decimal = DEFAULT_PATH_ALLOWANCE,
    ceiling: Length = STANDOFF_CEILING,
) -> Length:
    """How far back the source has to go for that many exposures to cover it."""
    if exposures <= 0:
        raise ValidationError("a joint needs at least one exposure")
    required = Decimal(360) / exposures / 2
    low, high = 1000, ceiling.um
    if usable_half_arc(joint, Length(high), allowance) < required:
        raise TechniqueError(
            "that many exposures will not cover the joint even at the ceiling",
            exposures=exposures,
            ceiling_mm=str(ceiling.mm),
        )
    for _ in range(50):
        middle = (low + high) // 2
        if usable_half_arc(joint, Length(middle), allowance) >= required:
            high = middle
        else:
            low = middle
    return Length(high)
