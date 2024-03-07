"""Beam geometry, and the unsharpness that falls out of it.

A source is not a point. It is a pellet three millimetres across, and every
point on it casts its own shadow of the same flaw in a slightly different
place. The shadows add up to a blurred edge whose width is the geometric
unsharpness, and if it is wider than the flaw the flaw disappears.

Three distances describe the shot. Source to film is what the tape measures.
Source to object is where the near surface of the weld sits. Object to film is
the rest. They add, and a geometry where they do not add is a typing mistake,
so the type refuses it rather than quietly computing something wrong.

    Ug = F x OFD / SOD

Everything about planning a shot is a fight with that fraction. Move the source
back and the unsharpness falls, but the exposure time goes up with the square
of the distance. Put the film hard against the pipe and OFD is just the wall,
which is why nobody leaves an air gap on purpose.

The limits are the ASME Section V Article 2 table, keyed on material thickness,
and they are steps rather than a curve. A weld one hair over two inches gets a
looser limit than one a hair under, which looks arbitrary and is exactly what
the code says.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_CEILING, Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.length import Length

_UNSHARPNESS_STEPS: tuple[tuple[Length, Length], ...] = (
    (Length.from_inch(2), Length.from_inch("0.020")),
    (Length.from_inch(3), Length.from_inch("0.030")),
    (Length.from_inch(4), Length.from_inch("0.040")),
)
_UNSHARPNESS_ABOVE = Length.from_inch("0.070")


def unsharpness_limit(material_thickness: Length) -> Length:
    """The most blur the code will accept at that thickness."""
    if material_thickness.um <= 0:
        raise ValidationError("a material thickness has to be positive")
    for ceiling, allowed in _UNSHARPNESS_STEPS:
        if material_thickness < ceiling:
            return allowed
    return _UNSHARPNESS_ABOVE


@dataclass(frozen=True, slots=True)
class BeamGeometry:
    """Where the source, the weld and the film are relative to each other."""

    source_to_object: Length
    object_to_film: Length
    focal_size: Length

    def __post_init__(self) -> None:
        if self.source_to_object.um <= 0:
            raise ValidationError("source to object distance has to be positive")
        if self.object_to_film.um < 0:
            raise ValidationError("object to film distance cannot be negative")
        if self.focal_size.um <= 0:
            raise ValidationError("a focal size has to be positive")

    @classmethod
    def from_sfd(cls, sfd: Length, object_to_film: Length, focal_size: Length) -> BeamGeometry:
        """Build from what the tape measured, which is source to film."""
        sod = sfd - object_to_film
        if sod.um <= 0:
            raise ValidationError(
                "the object does not fit between the source and the film",
                sfd_um=sfd.um,
                ofd_um=object_to_film.um,
            )
        return cls(source_to_object=sod, object_to_film=object_to_film, focal_size=focal_size)

    @property
    def source_to_film(self) -> Length:
        return self.source_to_object + self.object_to_film

    @property
    def geometric_unsharpness(self) -> Length:
        return Length(round(self.focal_size.um * self.object_to_film.um / self.source_to_object.um))

    @property
    def magnification(self) -> Decimal:
        return self.source_to_film.ratio(self.source_to_object)

    def meets(self, material_thickness: Length) -> bool:
        return self.geometric_unsharpness <= unsharpness_limit(material_thickness)

    def check(self, material_thickness: Length) -> None:
        limit = unsharpness_limit(material_thickness)
        actual = self.geometric_unsharpness
        if actual > limit:
            raise TechniqueError(
                "geometric unsharpness is over the limit for this thickness",
                unsharpness_mm=str(actual.mm),
                limit_mm=str(limit.mm),
                sod_mm=str(self.source_to_object.mm),
            )


def minimum_source_to_object(focal_size: Length, object_to_film: Length, limit: Length) -> Length:
    """The closest the source can be and still make the limit.

    Rearranged from the unsharpness formula. Rounded up, because a distance
    rounded down is a shot that fails the very check it was computed to pass.
    """
    if limit.um <= 0:
        raise ValidationError("an unsharpness limit has to be positive")
    if object_to_film.um == 0:
        return Length(1)
    exact = Decimal(focal_size.um * object_to_film.um) / Decimal(limit.um)
    return Length(int(exact.to_integral_value(rounding=ROUND_CEILING)))


def minimum_source_to_film(
    focal_size: Length, object_to_film: Length, material_thickness: Length
) -> Length:
    """What the technique sheet writes in the distance box."""
    limit = unsharpness_limit(material_thickness)
    return minimum_source_to_object(focal_size, object_to_film, limit) + object_to_film
