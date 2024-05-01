"""Where the indicators go, how many, and when a shim goes under one.

Three rules and every one of them gets broken on site.

One indicator per radiograph. Obvious until a single exposure covers the whole
circumference of a pipe, at which point one indicator says nothing about the
part of the weld a hundred and eighty degrees away, so a panoramic shot takes
three, spaced round the pipe.

The indicator goes on the source side. It is further from the film there, its
shadow is bigger and softer, and that is the point: if the technique can show a
softened indicator it can certainly show a sharp flaw. Film side is allowed
when the source side cannot be reached, the indicator is one row easier to make
up for it, and a lead F goes beside it so a reviewer knows.

An indicator lies on the parent metal, and the parent metal is thinner than the
weld with its cap. Reading a plaque that is sitting on ten millimetres when the
weld is thirteen flatters the technique. So a shim goes under the plaque to
make up the difference, the shim is the same material as the part, and it has
to be bigger than the plaque so its own edge does not land inside the area
being read.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry.joint import Joint
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import ClockPosition
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.quantities.length import Length

PANORAMIC_INDICATORS = 3
SHIM_MARGIN = Length.from_mm(3)
"""How far the shim has to stand proud of the plaque on every side."""


@dataclass(frozen=True, slots=True)
class Shim:
    """A pad of parent material under the indicator."""

    thickness: Length
    material_key: str

    def __post_init__(self) -> None:
        if self.thickness.um <= 0:
            raise ValidationError("a shim with no thickness is not a shim")

    def __str__(self) -> str:
        return f"{self.thickness.format_mm()} {self.material_key} shim"


@dataclass(frozen=True, slots=True)
class Placement:
    """One indicator, where it sits and what is under it."""

    side: IndicatorSide
    position: ClockPosition | None
    shim: Shim | None

    @property
    def needs_f_marker(self) -> bool:
        return self.side.needs_marking

    def describe(self) -> str:
        where = f" at {self.position}" if self.position is not None else ""
        under = f" on a {self.shim}" if self.shim is not None else ""
        marker = " with an F marker" if self.needs_f_marker else ""
        return f"{self.side}{where}{under}{marker}"


def shim_for(joint: Joint) -> Shim | None:
    """A shim is needed exactly when the cap stands proud of the parent metal."""
    if joint.reinforcement.um <= 0:
        return None
    return Shim(thickness=joint.reinforcement, material_key=joint.material.key)


def minimum_shim_size(plaque_long_side: Length) -> Length:
    """The shim has to stand proud of the plaque on every side."""
    if plaque_long_side.um <= 0:
        raise ValidationError("a plaque has a size")
    return plaque_long_side + SHIM_MARGIN * 2


def indicators_required(shot: ShotType, exposures: int) -> int:
    """One per radiograph, except a panoramic shot which takes three."""
    if exposures <= 0:
        raise ValidationError("a joint needs at least one exposure", exposures=exposures)
    if shot is ShotType.PANORAMIC:
        return PANORAMIC_INDICATORS
    return exposures


def panoramic_positions(count: int = PANORAMIC_INDICATORS) -> tuple[ClockPosition, ...]:
    """Spaced evenly round the pipe, starting at the top."""
    if count <= 0:
        raise ValidationError("a panoramic shot needs at least one indicator")
    step = Decimal(360) / count
    return tuple(ClockPosition.from_degrees(step * i) for i in range(count))


def plan_placements(
    joint: Joint,
    shot: ShotType,
    exposures: int,
    side: IndicatorSide = IndicatorSide.SOURCE,
) -> tuple[Placement, ...]:
    """Every indicator this joint needs, with its position and its shim."""
    count = indicators_required(shot, exposures)
    shim = shim_for(joint)
    if shot is ShotType.PANORAMIC:
        return tuple(
            Placement(side=side, position=position, shim=shim)
            for position in panoramic_positions(count)
        )
    if not joint.form.is_circumferential:
        return tuple(Placement(side=side, position=None, shim=shim) for _ in range(count))
    step = Decimal(360) / count
    return tuple(
        Placement(side=side, position=ClockPosition.from_degrees(step * i), shim=shim)
        for i in range(count)
    )


def check_shim_covers(plaque_long_side: Length, shim_side: Length) -> None:
    """Refuse a shim whose edge would land inside the area being read."""
    needed = minimum_shim_size(plaque_long_side)
    if shim_side < needed:
        raise TechniqueError(
            "the shim does not stand proud of the plaque",
            shim_mm=str(shim_side.mm),
            needed_mm=str(needed.mm),
        )
