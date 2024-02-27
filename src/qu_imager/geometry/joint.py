"""The joint under the film.

Everything downstream needs the same four numbers about a weld and they are
never all on the work order. Outside diameter and wall come off the line list.
Reinforcement is what the welder actually left, which is a measurement and not
a specification. The form of the joint decides which shots are even legal:
a girth weld in a six inch line can be shot through one wall from inside if
there is access, or through two walls from outside if there is not, and a
one inch line has no choice at all.

Diameter and wall together are the number that matters most. The ratio decides
whether the source can be put close enough for the geometric unsharpness to
come out, and the small bore threshold at 89 mm outside diameter is where the
elliptical technique starts being the only thing that works.

Plate has no diameter. The type carries that as an absence rather than a zero,
because a zero diameter would sail through arithmetic and come out as an
infinite circumference somewhere three modules away.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.materials import Material
from qu_imager.quantities.length import Length

SMALL_BORE_LIMIT = Length.from_mm("88.9")
"""Three and a half inch outside diameter, where elliptical shots take over."""


class JointForm(Enum):
    """What kind of weld this is, which decides what shots are available."""

    PIPE_GIRTH = "girth weld"
    PIPE_SEAM = "longitudinal seam"
    PLATE_BUTT = "plate butt weld"
    NOZZLE = "nozzle attachment weld"
    SOCKET = "socket weld"

    @property
    def is_circumferential(self) -> bool:
        return self in {JointForm.PIPE_GIRTH, JointForm.NOZZLE, JointForm.SOCKET}

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Joint:
    """A weld joint, described the way the shooter needs it described."""

    form: JointForm
    wall: Length
    material: Material
    outside_diameter: Length | None = None
    reinforcement: Length = Length.ZERO
    seam_length: Length | None = None

    def __post_init__(self) -> None:
        if self.wall.um <= 0:
            raise ValidationError("a wall thickness has to be positive", wall_um=self.wall.um)
        if self.reinforcement.um < 0:
            raise ValidationError("reinforcement cannot be negative", um=self.reinforcement.um)
        if self.form is JointForm.PLATE_BUTT:
            if self.outside_diameter is not None:
                raise ValidationError("plate has no outside diameter")
        else:
            if self.outside_diameter is None:
                raise ValidationError("a pipe joint needs an outside diameter")
            if self.outside_diameter.um <= 0:
                raise ValidationError("an outside diameter has to be positive")
            if self.wall.um * 2 >= self.outside_diameter.um:
                raise ValidationError(
                    "the wall closes the bore",
                    wall_um=self.wall.um,
                    od_um=self.outside_diameter.um,
                )

    @property
    def od(self) -> Length:
        """The outside diameter, refusing to be asked of plate."""
        if self.outside_diameter is None:
            raise ValidationError("plate has no outside diameter", form=self.form.value)
        return self.outside_diameter

    @property
    def inside_diameter(self) -> Length:
        return self.od - self.wall * 2

    @property
    def outside_circumference(self) -> Length:
        return _circumference(self.od)

    @property
    def inside_circumference(self) -> Length:
        return _circumference(self.inside_diameter)

    @property
    def mean_circumference(self) -> Length:
        return _circumference(self.od - self.wall)

    @property
    def diameter_to_wall(self) -> Decimal:
        """The ratio that decides whether a source will fit inside."""
        return self.od.ratio(self.wall)

    @property
    def is_small_bore(self) -> bool:
        if self.outside_diameter is None:
            return False
        return self.outside_diameter <= SMALL_BORE_LIMIT

    @property
    def total_thickness(self) -> Length:
        """Wall plus the cap, which is what the beam actually goes through."""
        return self.wall + self.reinforcement

    def admits_internal_source(self, source_diameter: Length, clearance: Length) -> bool:
        """Whether a projector head physically fits down the bore."""
        if self.outside_diameter is None:
            return False
        return self.inside_diameter.um >= (source_diameter + clearance * 2).um

    def describe(self) -> str:
        if self.outside_diameter is None:
            return f"{self.material} {self.form}, {self.wall.format_mm()} wall"
        return (
            f"{self.material} {self.form}, {self.od.format_mm()} od, {self.wall.format_mm()} wall"
        )


def _circumference(diameter: Length) -> Length:
    if diameter.um <= 0:
        raise ValidationError("a circumference needs a positive diameter", um=diameter.um)
    return Length(round(diameter.um * math.pi))
