"""Getting a source to the job, which is regulated harder than using it.

A projector in the back of a truck is a radioactive material package on a
public road, and the rules for that come from transport regulations rather than
from the radiography licence. Three things have to be right and they are
checked in this order.

The package has to be rated for the contents. A sealed radiography source is
special form, which means it has been tested to survive impact and fire without
dispersing, so a Type A package may carry up to the A1 value for the isotope.
Above that it takes a Type B, which is a different and much more expensive box.

The label depends on two numbers, not one: the rate at the surface of the
package and the rate at a metre from it. The metre reading, in millirem an hour
and rounded up to a tenth, is the transport index, and it is what limits how
many packages can ride together.

The vehicle has a ceiling on the sum of the transport indices aboard. A crew
carrying two projectors to a job can easily be over it without either package
being over anything on its own, which is the failure that turns up on a
roadside inspection.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_CEILING, Decimal
from enum import Enum

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import DoseRate
from qu_imager.sources.isotope import Isotope

VEHICLE_INDEX_CEILING = Decimal(50)
"""The sum of transport indices allowed on one exclusive use vehicle."""


class PackageType(Enum):
    EXCEPTED = "excepted package"
    TYPE_A = "Type A package"
    TYPE_B = "Type B package"

    def __str__(self) -> str:
        return self.value


class Label(Enum):
    WHITE_I = "White-I"
    YELLOW_II = "Yellow-II"
    YELLOW_III = "Yellow-III"

    def __str__(self) -> str:
        return self.value


# A1 in terabecquerels, for special form material
_A1_TBQ: dict[str, str] = {
    "ir192": "1",
    "co60": "0.4",
    "se75": "3",
    "cs137": "2",
    "yb169": "4",
}

_SURFACE_CEILINGS: tuple[tuple[Label, DoseRate], ...] = (
    (Label.WHITE_I, DoseRate.from_msv_per_hour("0.005")),
    (Label.YELLOW_II, DoseRate.from_msv_per_hour("0.5")),
    (Label.YELLOW_III, DoseRate.from_msv_per_hour(2)),
)

_INDEX_CEILINGS: dict[Label, Decimal] = {
    Label.WHITE_I: Decimal(0),
    Label.YELLOW_II: Decimal(1),
    Label.YELLOW_III: Decimal(10),
}


def a1_limit(isotope: Isotope) -> Activity:
    """The most special form material a Type A package may carry."""
    try:
        return Activity.from_tbq(_A1_TBQ[isotope.key])
    except KeyError:
        raise ConfigurationError(
            "no transport limit is published for that isotope", isotope=isotope.key
        ) from None


def transport_index(rate_at_one_metre: DoseRate) -> Decimal:
    """Millirem an hour at a metre, rounded up to a tenth."""
    if rate_at_one_metre.nsv_per_hour < 0:
        raise ValidationError("a transport index needs a rate")
    mrem = rate_at_one_metre.usv_per_hour / 10
    return mrem.quantize(Decimal("0.1"), rounding=ROUND_CEILING)


def label_for(surface_rate: DoseRate, rate_at_one_metre: DoseRate) -> Label:
    """The label the package carries, decided by whichever number is worse."""
    index = transport_index(rate_at_one_metre)
    for label, ceiling in _SURFACE_CEILINGS:
        if surface_rate <= ceiling and index <= _INDEX_CEILINGS[label]:
            return label
    raise TechniqueError(
        "the package is over the limits for every label",
        surface_msv_per_hour=str(surface_rate.msv_per_hour),
        transport_index=str(index),
    )


@dataclass(frozen=True, slots=True)
class Package:
    """One device, boxed, as it goes on the truck."""

    reference: str
    isotope: Isotope
    activity: Activity
    package_type: PackageType
    surface_rate: DoseRate
    rate_at_one_metre: DoseRate

    def __post_init__(self) -> None:
        if not self.reference.strip():
            raise ValidationError("a package has a reference")

    @property
    def transport_index(self) -> Decimal:
        return transport_index(self.rate_at_one_metre)

    @property
    def label(self) -> Label:
        return label_for(self.surface_rate, self.rate_at_one_metre)

    def faults(self) -> tuple[str, ...]:
        """Everything wrong with the package before it leaves the yard."""
        out: list[str] = []
        if self.package_type is PackageType.TYPE_A:
            limit = a1_limit(self.isotope)
            if self.activity > limit:
                out.append(
                    f"{self.activity.format_curie()} is over the "
                    f"{limit.format_tbq()} A1 limit for a Type A package"
                )
        if self.package_type is PackageType.EXCEPTED:
            out.append("a radiography source never travels as an excepted package")
        try:
            _ = self.label
        except TechniqueError as over:
            out.append(over.message)
        return tuple(out)


@dataclass(frozen=True, slots=True)
class Shipment:
    """Everything on one truck."""

    packages: tuple[Package, ...]

    @property
    def total_index(self) -> Decimal:
        return sum((p.transport_index for p in self.packages), Decimal(0))

    def over_vehicle_ceiling(self) -> bool:
        return self.total_index > VEHICLE_INDEX_CEILING

    def faults(self) -> tuple[str, ...]:
        out: list[str] = []
        for package in self.packages:
            out.extend(f"{package.reference}: {fault}" for fault in package.faults())
        if self.over_vehicle_ceiling():
            out.append(
                f"the transport indices total {self.total_index}, over the "
                f"vehicle ceiling of {VEHICLE_INDEX_CEILING}"
            )
        return tuple(out)
