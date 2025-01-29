"""Deliberate magnification, which is the one technique that gets better by moving the film away.

Everything else in this package wants the film hard against the part, because
an air gap magnifies the geometric unsharpness. Magnification technique does
the opposite on purpose: pull the film back, let the image grow, and read a
flaw that was smaller than the detector could resolve.

It only works with a very small focal spot. The image grows by the
magnification and so does the geometric unsharpness, so the trade is only
favourable while the detector blur, shrunk by the magnification, is still the
thing setting the limit.

The optimum is derived from this package's own model rather than quoted. The
total unsharpness referred back to the part, with the two terms added in
quadrature the way the duplex gauge measures them, is

    U(M) = sqrt( (F(M-1))^2 + Ud^2 ) / M

and differentiating that gives the minimum at

    M = 1 + (Ud / F)^2

Textbooks quote several other exponents for this and they are not wrong, they
are minimising something else: image plane unsharpness against feature size,
or detectability, rather than object referred blur. Quoting one of those
alongside a quadrature model would give a number that does not minimise the
expression it sits next to, which is worse than being crude.

The derived optimum runs away at high ratios, so there is a practical ceiling
as well. Output falls with the square of the distance, and a magnification of
fifty turns a two minute shot into an hour.

This is a microfocus technique. On a three millimetre iridium pellet the
optimum magnification is barely above one and the whole thing is pointless,
which the code says rather than computing a number nobody should use.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.length import Length

MICROFOCUS_CEILING = Length.from_mm("0.4")
"""Above this focal size magnification buys nothing worth having."""

USEFUL_MAGNIFICATION = Decimal("1.2")
"""Below this the film may as well be against the part."""

PRACTICAL_CEILING = Decimal(20)
"""Above this the exposure time makes the technique academic."""


@dataclass(frozen=True, slots=True)
class Setup:
    """A magnification geometry, described by the two distances."""

    source_to_object: Length
    object_to_film: Length
    focal_size: Length
    detector_unsharpness: Length

    def __post_init__(self) -> None:
        if self.source_to_object.um <= 0:
            raise ValidationError("the source stands off the part")
        if self.object_to_film.um < 0:
            raise ValidationError("an air gap is not negative")
        if self.focal_size.um <= 0:
            raise ValidationError("a focal spot has a size")
        if self.detector_unsharpness.um <= 0:
            raise ValidationError("a detector has an unsharpness")

    @property
    def magnification(self) -> Decimal:
        total = self.source_to_object + self.object_to_film
        return total.ratio(self.source_to_object)

    @property
    def geometric_unsharpness(self) -> Length:
        return Length(round(self.focal_size.um * self.object_to_film.um / self.source_to_object.um))

    @property
    def effective_detector_unsharpness(self) -> Length:
        """The detector blur as it appears back on the part, shrunk by the magnification."""
        return Length(int(Decimal(self.detector_unsharpness.um) / self.magnification))

    @property
    def total_unsharpness(self) -> Length:
        """The two terms in quadrature, referred back to the part."""
        geometric = Decimal(self.geometric_unsharpness.um) / self.magnification
        detector = Decimal(self.effective_detector_unsharpness.um)
        return Length(int((geometric * geometric + detector * detector).sqrt()))

    @property
    def is_worth_doing(self) -> bool:
        return self.magnification >= USEFUL_MAGNIFICATION


def theoretical_optimum(focal_size: Length, detector_unsharpness: Length) -> Decimal:
    """Where the model says the object referred blur is least."""
    if focal_size.um <= 0 or detector_unsharpness.um <= 0:
        raise ValidationError("both unsharpness terms have to be positive")
    if focal_size > MICROFOCUS_CEILING:
        raise TechniqueError(
            "magnification needs a microfocus source",
            focal_size_mm=str(focal_size.mm),
            ceiling_mm=str(MICROFOCUS_CEILING.mm),
        )
    with localcontext() as ctx:
        ctx.prec = 28
        ratio = Decimal(detector_unsharpness.um) / Decimal(focal_size.um)
        return Decimal(1) + ratio * ratio


def optimum_magnification(focal_size: Length, detector_unsharpness: Length) -> Decimal:
    """The optimum a shooter can actually use, capped at the practical ceiling."""
    best = theoretical_optimum(focal_size, detector_unsharpness)
    return min(best, PRACTICAL_CEILING)


def distances_for(magnification: Decimal, source_to_film: Length) -> tuple[Length, Length]:
    """Split a fixed source to film distance to reach a wanted magnification."""
    if magnification < 1:
        raise ValidationError("a magnification is at least one")
    if source_to_film.um <= 0:
        raise ValidationError("a source to film distance has to be positive")
    sod = Length(int(Decimal(source_to_film.um) / magnification))
    return (sod, Length(source_to_film.um - sod.um))


def is_microfocus(focal_size: Length) -> bool:
    return focal_size <= MICROFOCUS_CEILING
