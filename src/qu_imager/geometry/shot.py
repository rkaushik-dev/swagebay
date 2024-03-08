"""Shot types, what each one penetrates, and how many of them a joint needs.

There are only a handful of ways to point a source at a girth weld and every
one of them is a compromise.

Panoramic puts the source in the middle of the pipe and shoots the whole
circumference at once. One exposure, one wall of steel, the best image anyone
is going to get. It needs access to the bore and a crawler or a long enough
guide tube, so it is available on a fabrication shop floor and almost never on
a tie in.

Single wall single image puts the source inside against the wall and the film
outside opposite it. Still one wall of steel, but it takes several exposures to
get round.

Double wall single image is the outside job. The beam goes through the far wall
and the near wall, and only the near wall is interpreted. Two walls of steel to
penetrate for one wall of weld, which is the price of not having access.

Double wall double image is the small bore job. The beam goes through both
walls, both are on the film, and the joint is offset so the two images come out
as an ellipse instead of on top of each other. It only works when the pipe is
small enough that the two images can be separated, which the code puts at three
and a half inches.

Exposure counts are the code minimums for complete coverage, not a guess, and
they are the reason a small bore weld costs three times what its size suggests.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import TechniqueError
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.quantities.length import Length


class ShotType(Enum):
    PANORAMIC = "panoramic"
    SWSI = "single wall single image"
    DWSI = "double wall single image"
    DWDI_ELLIPTICAL = "double wall double image, elliptical"
    DWDI_SUPERIMPOSED = "double wall double image, superimposed"
    CONTACT = "contact"

    @property
    def walls_penetrated(self) -> int:
        if self in {ShotType.PANORAMIC, ShotType.SWSI, ShotType.CONTACT}:
            return 1
        return 2

    @property
    def walls_interpreted(self) -> int:
        if self in {ShotType.DWDI_ELLIPTICAL, ShotType.DWDI_SUPERIMPOSED}:
            return 2
        return 1

    @property
    def needs_internal_access(self) -> bool:
        return self in {ShotType.PANORAMIC, ShotType.SWSI}

    @property
    def minimum_exposures(self) -> int:
        """The code minimum for complete coverage of a girth weld."""
        return _MINIMUM_EXPOSURES[self]

    @property
    def exposure_separation_degrees(self) -> int:
        return _SEPARATION[self]

    def __str__(self) -> str:
        return self.value


_MINIMUM_EXPOSURES: dict[ShotType, int] = {
    ShotType.PANORAMIC: 1,
    ShotType.SWSI: 3,
    ShotType.DWSI: 3,
    ShotType.DWDI_ELLIPTICAL: 2,
    ShotType.DWDI_SUPERIMPOSED: 3,
    ShotType.CONTACT: 1,
}

_SEPARATION: dict[ShotType, int] = {
    ShotType.PANORAMIC: 360,
    ShotType.SWSI: 120,
    ShotType.DWSI: 120,
    ShotType.DWDI_ELLIPTICAL: 90,
    ShotType.DWDI_SUPERIMPOSED: 60,
    ShotType.CONTACT: 360,
}


def penetrated_thickness(joint: Joint, shot: ShotType) -> Length:
    """How much steel the beam actually crosses, cap included.

    The cap counts once per wall the beam goes through, because a beam that
    crosses two walls crosses two caps. A double wall shot on a capped weld is
    therefore noticeably slower than the wall thickness alone suggests, and
    getting this wrong is the classic reason a first shot comes out thin.
    """
    walls = shot.walls_penetrated
    return joint.wall * walls + joint.reinforcement * walls


def exposures_required(joint: Joint, shot: ShotType) -> int:
    """The code minimum, before anything about coverage geometry is applied."""
    if not joint.form.is_circumferential:
        return 1
    return shot.minimum_exposures


@dataclass(frozen=True, slots=True)
class ShotFeasibility:
    """Whether a shot type is available on this joint, and why not."""

    shot: ShotType
    available: bool
    reason: str | None = None


def feasible_shots(
    joint: Joint,
    source_diameter: Length,
    guide_clearance: Length,
) -> tuple[ShotFeasibility, ...]:
    """Every shot type, with a plain reason against the ones that are out."""
    results: list[ShotFeasibility] = []
    inside = joint.admits_internal_source(source_diameter, guide_clearance)
    for shot in ShotType:
        reason = _reason_against(joint, shot, inside)
        results.append(ShotFeasibility(shot, reason is None, reason))
    return tuple(results)


def _reason_against(joint: Joint, shot: ShotType, inside: bool) -> str | None:
    if shot.needs_internal_access and not inside:
        return "no room for the source inside the bore"
    if shot in {ShotType.DWDI_ELLIPTICAL, ShotType.DWDI_SUPERIMPOSED}:
        if joint.form is JointForm.PLATE_BUTT:
            return "plate has no second wall"
        if not joint.is_small_bore:
            return "double wall double image is limited to 89 mm outside diameter"
    if shot is ShotType.DWSI and joint.form is JointForm.PLATE_BUTT:
        return "plate has no second wall"
    if shot is ShotType.PANORAMIC and not joint.form.is_circumferential:
        return "a panoramic shot needs a circumferential weld"
    return None


def choose_shot(
    joint: Joint,
    source_diameter: Length,
    guide_clearance: Length,
) -> ShotType:
    """The shot the office would pick, cheapest and cleanest first."""
    order = (
        ShotType.PANORAMIC,
        ShotType.SWSI,
        ShotType.DWDI_ELLIPTICAL,
        ShotType.DWSI,
        ShotType.DWDI_SUPERIMPOSED,
        ShotType.CONTACT,
    )
    available = {
        f.shot for f in feasible_shots(joint, source_diameter, guide_clearance) if f.available
    }
    for shot in order:
        if shot in available:
            return shot
    raise TechniqueError("no shot type is available on this joint", joint=joint.describe())


def coverage_arc(joint: Joint, exposures: int) -> Decimal:
    """The arc of weld each exposure has to carry, in degrees."""
    if exposures <= 0:
        raise TechniqueError("a joint needs at least one exposure", exposures=exposures)
    if not joint.form.is_circumferential:
        return Decimal(0)
    return Decimal(360) / exposures
