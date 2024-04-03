"""Shielding, in half value layers.

A half value layer is the thickness of something that cuts the rate in half.
Ten of them cut it by a factor of a thousand and a bit. That is the whole
model, and it is deliberately the crude one: broad beam attenuation with
scatter buildup already folded into the published layer thicknesses, which is
how the numbers in the licence application were derived and therefore how the
numbers here have to be derived if the two are going to agree.

The tables are per isotope and per material and they are not interchangeable.
Iridium goes through five millimetres of lead the way cobalt goes through
twelve, and a barrier sized for one and used for the other is off by more than
a factor of two.

What this module will not do is size a barrier from first principles for a
material that is not in the table. There is no formula here that turns density
into attenuation. If the wall is made of something unlisted, somebody measures
it with a meter and enters the layer thickness, which is what the health
physicist would have made them do anyway.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_CEILING, Decimal, localcontext

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.sources.isotope import Isotope


@dataclass(frozen=True, slots=True)
class Barrier:
    """A thickness of one shielding material."""

    material: str
    thickness: Length

    def __post_init__(self) -> None:
        if self.thickness.um < 0:
            raise ValidationError("a barrier cannot be a negative thickness")

    def transmission(self, source: Isotope) -> Decimal:
        return transmission(source, self.material, self.thickness)

    def layers(self, source: Isotope) -> Decimal:
        hvl = half_value_layer(source, self.material)
        return self.thickness.ratio(hvl)

    def __str__(self) -> str:
        return f"{self.thickness.format_mm()} of {self.material}"


# half value layers in millimetres, broad beam, from the licence application
_HALF_VALUE_LAYERS: dict[str, dict[str, str]] = {
    "ir192": {
        "lead": "4.8",
        "steel": "12.7",
        "concrete": "44.5",
        "tungsten": "2.8",
        "uranium": "2.0",
        "water": "160.0",
    },
    "co60": {
        "lead": "12.5",
        "steel": "21.6",
        "concrete": "62.0",
        "tungsten": "7.6",
        "uranium": "5.5",
        "water": "220.0",
    },
    "cs137": {
        "lead": "6.5",
        "steel": "16.5",
        "concrete": "47.0",
        "tungsten": "4.0",
        "uranium": "3.0",
        "water": "180.0",
    },
    "se75": {
        "lead": "1.0",
        "steel": "8.0",
        "concrete": "30.0",
        "tungsten": "0.7",
        "uranium": "0.5",
        "water": "120.0",
    },
    "yb169": {
        "lead": "0.35",
        "steel": "3.5",
        "concrete": "15.0",
        "tungsten": "0.3",
        "uranium": "0.2",
        "water": "70.0",
    },
}

SHIELD_MATERIALS = ("concrete", "lead", "steel", "tungsten", "uranium", "water")


def half_value_layer(source: Isotope, material: str) -> Length:
    """The thickness that halves the rate, for that isotope in that stuff."""
    try:
        layers = _HALF_VALUE_LAYERS[source.key]
    except KeyError:
        raise ConfigurationError("no shielding data for that isotope", isotope=source.key) from None
    try:
        return Length.from_mm(layers[material])
    except KeyError:
        raise ConfigurationError(
            "no half value layer is published for that material",
            isotope=source.key,
            material=material,
        ) from None


def tenth_value_layer(source: Isotope, material: str) -> Length:
    """What the wall charts are drawn in. It is log ten over log two layers."""
    hvl = half_value_layer(source, material)
    with localcontext() as ctx:
        ctx.prec = 28
        factor = Decimal(10).ln() / Decimal(2).ln()
    return Length(int(Decimal(hvl.um) * factor))


def transmission(source: Isotope, material: str, thickness: Length) -> Decimal:
    """The fraction that gets through."""
    if thickness.um < 0:
        raise ValidationError("a shield cannot be a negative thickness")
    if thickness.um == 0:
        return Decimal(1)
    hvl = half_value_layer(source, material)
    with localcontext() as ctx:
        ctx.prec = 28
        layers = Decimal(thickness.um) / Decimal(hvl.um)
        return (-layers * Decimal(2).ln()).exp()


def attenuated(source: Isotope, material: str, thickness: Length, rate: DoseRate) -> DoseRate:
    return DoseRate(int(Decimal(rate.nsv_per_hour) * transmission(source, material, thickness)))


def thickness_for_transmission(source: Isotope, material: str, fraction: Decimal) -> Length:
    """How much of it is needed to cut the rate to that fraction."""
    if not 0 < fraction <= 1:
        raise ValidationError("a transmission fraction sits in (0, 1]", fraction=str(fraction))
    hvl = half_value_layer(source, material)
    with localcontext() as ctx:
        ctx.prec = 28
        layers = -fraction.ln() / Decimal(2).ln()
        micrometres = Decimal(hvl.um) * layers
    return Length(int(micrometres.to_integral_value(rounding=ROUND_CEILING)))


def thickness_for_rate(
    source: Isotope, material: str, unshielded: DoseRate, target: DoseRate
) -> Length:
    """The barrier that brings an unshielded rate down to a target."""
    if target.nsv_per_hour <= 0:
        raise ValidationError("a target rate has to be positive")
    if unshielded.nsv_per_hour <= target.nsv_per_hour:
        return Length.ZERO
    fraction = Decimal(target.nsv_per_hour) / Decimal(unshielded.nsv_per_hour)
    return thickness_for_transmission(source, material, fraction)


def materials_for(source: Isotope) -> tuple[str, ...]:
    return tuple(sorted(_HALF_VALUE_LAYERS.get(source.key, {})))
