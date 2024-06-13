"""The radiation field round a shot, and where the rope goes.

Everything on this side of the package answers one question: how far back does
everybody have to be. The answer is a distance for each of several boundaries,
each defined by a dose rate, and the rates are not ours. They are in the
licence.

The field is the source rate, cut down by whatever is between the source and
the person. A collimator cuts it by a published fraction outside its aperture.
A barrier cuts it by half value layers. Both apply, and they apply in that
order, which matters because a collimator is directional and a barrier is not.

The one thing this model will not do is pretend a bearing it was not given.
Asking for the rate somewhere requires saying where that somewhere is relative
to the beam, because the collimated answer and the uncollimated answer differ
by a factor of twenty and getting it backwards puts the rope in the wrong
place.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import ROUND_CEILING, Decimal, localcontext
from enum import Enum

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.safety.shielding import Barrier
from qu_imager.sources.device import Collimator
from qu_imager.sources.isotope import Isotope
from qu_imager.sources.output import output_at_one_metre


class BoundaryKind(Enum):
    """The postings the licence defines, softest first."""

    RESTRICTED = "restricted area"
    RADIATION_AREA = "radiation area"
    HIGH_RADIATION = "high radiation area"

    @property
    def rate(self) -> DoseRate:
        return _BOUNDARY_RATES[self]

    @property
    def sign_reads(self) -> str:
        return _SIGN_TEXT[self]

    def __str__(self) -> str:
        return self.value


_BOUNDARY_RATES: dict[BoundaryKind, DoseRate] = {
    BoundaryKind.RESTRICTED: DoseRate.from_mr_per_hour(2),
    BoundaryKind.RADIATION_AREA: DoseRate.from_mr_per_hour(5),
    BoundaryKind.HIGH_RADIATION: DoseRate.from_mr_per_hour(100),
}

_SIGN_TEXT: dict[BoundaryKind, str] = {
    BoundaryKind.RESTRICTED: "caution radiation area",
    BoundaryKind.RADIATION_AREA: "caution radiation area",
    BoundaryKind.HIGH_RADIATION: "danger high radiation area",
}


@dataclass(frozen=True, slots=True)
class RadiationField:
    """A source, what is around it, and what that leaves at a distance."""

    isotope: Isotope
    activity: Activity
    collimator: Collimator | None = None
    barriers: tuple[Barrier, ...] = field(default_factory=tuple)

    @property
    def unshielded_at_one_metre(self) -> DoseRate:
        return output_at_one_metre(self.isotope, self.activity)

    def attenuation(self, bearing_degrees: Decimal) -> Decimal:
        """Everything between the source and that bearing, multiplied together."""
        total = Decimal(1)
        if self.collimator is not None:
            total *= self.collimator.factor_at(bearing_degrees)
        for barrier in self.barriers:
            total *= barrier.transmission(self.isotope)
        return total

    def rate_at(self, distance: Length, bearing_degrees: Decimal) -> DoseRate:
        """The rate a person standing there would read."""
        if distance.um <= 0:
            raise ValidationError("a rate needs a distance from the source")
        metres = distance.m
        with localcontext() as ctx:
            ctx.prec = 28
            unshielded = Decimal(self.unshielded_at_one_metre.nsv_per_hour) / (metres * metres)
            return DoseRate(int(unshielded * self.attenuation(bearing_degrees)))

    def distance_to(self, target: DoseRate, bearing_degrees: Decimal) -> Length:
        """How far back that rate is reached. Always rounded away from the source."""
        if target.nsv_per_hour <= 0:
            raise ValidationError("a boundary needs a positive rate")
        attenuated = Decimal(self.unshielded_at_one_metre.nsv_per_hour) * self.attenuation(
            bearing_degrees
        )
        if attenuated <= 0:
            return Length.ZERO
        with localcontext() as ctx:
            ctx.prec = 28
            metres = (attenuated / Decimal(target.nsv_per_hour)).sqrt()
            micrometres = metres * 1_000_000
        return Length(int(micrometres.to_integral_value(rounding=ROUND_CEILING)))

    def distance_from_reading(
        self, reading: DoseRate, taken_at: Length, target: DoseRate
    ) -> Length:
        """The same question, started from a meter reading rather than the table.

        A crew that has walked out and read forty millirem at ten metres knows
        more than the table does, because the reading has whatever scatter the
        site produces already in it. Projecting that reading is the honest way
        to place a boundary and it is what the procedure asks for whenever a
        meter is to hand.
        """
        if target.nsv_per_hour <= 0:
            raise ValidationError("a boundary needs a positive rate")
        if taken_at.um <= 0:
            raise ValidationError("a reading needs a distance")
        if reading.nsv_per_hour == 0:
            return Length.ZERO
        with localcontext() as ctx:
            ctx.prec = 28
            ratio = (Decimal(reading.nsv_per_hour) / Decimal(target.nsv_per_hour)).sqrt()
            micrometres = Decimal(taken_at.um) * ratio
        return Length(int(micrometres.to_integral_value(rounding=ROUND_CEILING)))

    def boundary(self, kind: BoundaryKind, bearing_degrees: Decimal) -> Length:
        return self.distance_to(kind.rate, bearing_degrees)

    def boundaries(self, bearing_degrees: Decimal) -> dict[BoundaryKind, Length]:
        return {kind: self.boundary(kind, bearing_degrees) for kind in BoundaryKind}

    def is_inside(self, kind: BoundaryKind, distance: Length, bearing_degrees: Decimal) -> bool:
        return self.rate_at(distance, bearing_degrees) > kind.rate


@dataclass(frozen=True, slots=True)
class RopeOff:
    """The distances the crew actually walks out and pegs."""

    down_beam: Length
    off_beam: Length
    kind: BoundaryKind

    @property
    def is_directional(self) -> bool:
        return self.down_beam != self.off_beam

    def describe(self) -> str:
        if not self.is_directional:
            return f"{self.kind} at {self.down_beam.format_m()}"
        return (
            f"{self.kind} at {self.down_beam.format_m()} down the beam and "
            f"{self.off_beam.format_m()} to the sides"
        )


def rope_off(
    radiation_field: RadiationField, kind: BoundaryKind = BoundaryKind.RESTRICTED
) -> RopeOff:
    """The two distances that go on the sketch, down the beam and off it."""
    down = radiation_field.boundary(kind, Decimal(0))
    off = radiation_field.boundary(kind, Decimal(180))
    if down.um == 0:
        raise TechniqueError("there is no source to rope off")
    return RopeOff(down_beam=down, off_beam=off, kind=kind)
