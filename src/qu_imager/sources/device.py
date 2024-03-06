"""Exposure devices and collimators.

The device is the yellow box. It holds the source in a shielded S tube, it has
a crank that drives the pigtail out through a guide tube to the weld, and it
has a type approval certificate that says how hot it reads at the surface and
at a metre with a full load in it. Those two numbers are not calculated by
anybody here. They are measured by the manufacturer on the type test and they
go on the certificate, so this module carries them as data and the shielding
model reads them rather than deriving them.

Capacity matters because a device rated for 130 curies of iridium will not be
loaded with 150, and a device rated for iridium will not take cobalt at all.
The check is per isotope and it is refused rather than warned about.

A collimator is a tungsten cup that goes on the end of the guide tube. It cuts
the field down to the sector pointed at the film and it is the single cheapest
thing a crew can do to shrink the boundary they have to rope off. The
attenuation is directional: full beam through the aperture, a published
transmission fraction everywhere else.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.sources.isotope import Isotope, isotope


@dataclass(frozen=True, slots=True)
class Collimator:
    """A directional shield on the end of the guide tube."""

    key: str
    name: str
    material: str
    aperture_degrees: int
    transmission: Decimal
    """Fraction of the unshielded rate that gets through the body of the cup."""
    mass_kg: Decimal

    def __post_init__(self) -> None:
        if not 0 < self.aperture_degrees <= 360:
            raise ValidationError("an aperture is an angle", degrees=self.aperture_degrees)
        if not 0 < self.transmission <= 1:
            raise ValidationError(
                "a transmission fraction sits in (0, 1]", transmission=str(self.transmission)
            )

    def covers(self, bearing_degrees: Decimal) -> bool:
        """Whether a bearing off the beam axis is inside the open sector."""
        half = Decimal(self.aperture_degrees) / 2
        return abs(_wrap(bearing_degrees)) <= half

    def factor_at(self, bearing_degrees: Decimal) -> Decimal:
        return Decimal(1) if self.covers(bearing_degrees) else self.transmission


@dataclass(frozen=True, slots=True)
class DeviceModel:
    """A type approved exposure device, as its certificate describes it."""

    key: str
    name: str
    manufacturer: str
    isotope_key: str
    capacity: Activity
    surface_rate_at_capacity: DoseRate
    metre_rate_at_capacity: DoseRate
    mass_kg: Decimal
    max_guide_tube: Length
    iso_category: str

    @property
    def isotope(self) -> Isotope:
        return isotope(self.isotope_key)

    def accepts(self, source_isotope: Isotope, activity: Activity) -> bool:
        return source_isotope.key == self.isotope_key and activity <= self.capacity

    def check_load(self, source_isotope: Isotope, activity: Activity) -> None:
        """Refuse a load the certificate does not cover."""
        if source_isotope.key != self.isotope_key:
            raise ConfigurationError(
                "this device is not certified for that isotope",
                device=self.key,
                certified_for=self.isotope_key,
                offered=source_isotope.key,
            )
        if activity > self.capacity:
            raise ConfigurationError(
                "load exceeds the certified capacity",
                device=self.key,
                capacity_ci=str(self.capacity.curie),
                offered_ci=str(activity.curie),
            )

    def surface_rate_with(self, activity: Activity) -> DoseRate:
        """The certificate rate scaled to the load actually in the device."""
        return self.surface_rate_at_capacity * activity.ratio(self.capacity)

    def metre_rate_with(self, activity: Activity) -> DoseRate:
        return self.metre_rate_at_capacity * activity.ratio(self.capacity)


def _wrap(degrees: Decimal) -> Decimal:
    """Fold a bearing into the half open interval from minus 180 to 180."""
    turned = degrees % Decimal(360)
    return turned - 360 if turned > 180 else turned


_DEVICES: dict[str, DeviceModel] = {
    d.key: d
    for d in [
        DeviceModel(
            key="880d",
            name="880 Delta",
            manufacturer="QSA",
            isotope_key="ir192",
            capacity=Activity.from_curie(130),
            surface_rate_at_capacity=DoseRate.from_msv_per_hour("1.8"),
            metre_rate_at_capacity=DoseRate.from_usv_per_hour(18),
            mass_kg=Decimal("22.2"),
            max_guide_tube=Length.from_m(12),
            iso_category="II",
        ),
        DeviceModel(
            key="880s",
            name="880 Sigma",
            manufacturer="QSA",
            isotope_key="ir192",
            capacity=Activity.from_curie(150),
            surface_rate_at_capacity=DoseRate.from_msv_per_hour("1.9"),
            metre_rate_at_capacity=DoseRate.from_usv_per_hour(19),
            mass_kg=Decimal("24.5"),
            max_guide_tube=Length.from_m(12),
            iso_category="II",
        ),
        DeviceModel(
            key="tsi3",
            name="Gammamat TSI 3",
            manufacturer="Isotopen",
            isotope_key="ir192",
            capacity=Activity.from_curie(120),
            surface_rate_at_capacity=DoseRate.from_msv_per_hour("1.5"),
            metre_rate_at_capacity=DoseRate.from_usv_per_hour(15),
            mass_kg=Decimal("18.0"),
            max_guide_tube=Length.from_m(9),
            iso_category="II",
        ),
        DeviceModel(
            key="spec150",
            name="SPEC 150",
            manufacturer="SPEC",
            isotope_key="se75",
            capacity=Activity.from_curie(100),
            surface_rate_at_capacity=DoseRate.from_msv_per_hour("0.9"),
            metre_rate_at_capacity=DoseRate.from_usv_per_hour(9),
            mass_kg=Decimal("11.3"),
            max_guide_tube=Length.from_m(9),
            iso_category="I",
        ),
        DeviceModel(
            key="tk100",
            name="Gammamat TK 100",
            manufacturer="Isotopen",
            isotope_key="co60",
            capacity=Activity.from_curie(45),
            surface_rate_at_capacity=DoseRate.from_msv_per_hour("1.9"),
            metre_rate_at_capacity=DoseRate.from_usv_per_hour(19),
            mass_kg=Decimal("118.0"),
            max_guide_tube=Length.from_m(6),
            iso_category="II",
        ),
    ]
}

_COLLIMATORS: dict[str, Collimator] = {
    c.key: c
    for c in [
        Collimator("none", "no collimator", "none", 360, Decimal(1), Decimal(0)),
        Collimator("w60", "tungsten 60 degree", "tungsten", 60, Decimal("0.06"), Decimal("7.5")),
        Collimator("w90", "tungsten 90 degree", "tungsten", 90, Decimal("0.08"), Decimal("6.2")),
        Collimator("du45", "depleted uranium 45", "uranium", 45, Decimal("0.03"), Decimal("9.1")),
    ]
}


def device_model(key: str) -> DeviceModel:
    try:
        return _DEVICES[key]
    except KeyError:
        raise ConfigurationError("no such exposure device", key=key) from None


def collimator(key: str) -> Collimator:
    try:
        return _COLLIMATORS[key]
    except KeyError:
        raise ConfigurationError("no such collimator", key=key) from None


def known_devices() -> tuple[str, ...]:
    return tuple(sorted(_DEVICES))


def known_collimators() -> tuple[str, ...]:
    return tuple(sorted(_COLLIMATORS))


def devices_for(source_isotope: Isotope) -> tuple[DeviceModel, ...]:
    fits = [d for d in _DEVICES.values() if d.isotope_key == source_isotope.key]
    return tuple(sorted(fits, key=lambda d: d.capacity.bq, reverse=True))
