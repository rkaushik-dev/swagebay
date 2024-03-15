"""Image quality indicators, hole type and wire type.

An indicator is a deliberately crude object placed in the beam so that the
image can be asked a question it can answer honestly: can you see this. It says
nothing about whether the weld is good. It says whether the radiograph is good
enough to be allowed to have an opinion about the weld.

Two families are in use and they measure different things. A hole type
penetrameter is a plaque of known thickness with three drilled holes at one,
two and four times that thickness. Seeing the essential hole means the
technique resolves a change in thickness of that size over that area. A wire
indicator is a row of wires of decreasing diameter, and seeing the essential
wire means the technique resolves a feature of that width.

Neither is a measure of the other. A radiograph that shows a 2T hole may not
show the wire the equivalence table says goes with it, which is why the table
in E747 is an equivalence and not an identity, and why a client specification
naming one family cannot be satisfied by shooting the other.

Identity numbers are thousandths of an inch, because that is what is stamped on
the lead and no amount of metrication has changed it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.length import Length


class HoleSize(Enum):
    """Which hole in the plaque has to be visible."""

    ONE_T = 1
    TWO_T = 2
    FOUR_T = 4

    @property
    def label(self) -> str:
        return f"{self.value}T"

    def __str__(self) -> str:
        return self.label


@dataclass(frozen=True, slots=True)
class HoleIndicator:
    """One ASTM E1025 plaque, identified by its thickness in mils."""

    identity: int

    def __post_init__(self) -> None:
        if self.identity not in _HOLE_IDENTITIES:
            raise ConfigurationError("no such hole type identity", identity=self.identity)

    @property
    def thickness(self) -> Length:
        return Length.from_thou(self.identity)

    def hole_diameter(self, hole: HoleSize) -> Length:
        """The drilled diameter, with the small plaque floor applied.

        Below ten mils the plaque is too thin to drill a 1T hole that survives
        handling, so the standard puts a floor on the diameter and the hole
        stops being a multiple of the thickness.
        """
        nominal = self.thickness * hole.value
        floor = Length.from_thou(10)
        return max(nominal, floor)

    def thickness_sensitivity_at(self, penetrated: Length) -> Decimal:
        """Plaque thickness as a percentage of what the beam crossed."""
        if penetrated.um <= 0:
            raise ValidationError("a penetrated thickness has to be positive")
        return self.thickness.ratio(penetrated) * 100

    def equivalent_sensitivity_at(self, penetrated: Length, hole: HoleSize) -> Decimal:
        """Equivalent penetrameter sensitivity, the E1025 appendix formula.

        A thin plaque with a big hole and a thick plaque with a small hole can
        be equally hard to see, and this is the number that says so. It falls
        out of the area of the hole as much as the thickness of the plaque,
        which is why a 4T hole on a thin plaque buys so little.
        """
        if penetrated.um <= 0:
            raise ValidationError("a penetrated thickness has to be positive")
        thickness = Decimal(self.thickness.um)
        diameter = Decimal(self.hole_diameter(hole).um)
        return 100 * (thickness * diameter / 2).sqrt() / Decimal(penetrated.um)

    def label(self) -> str:
        return str(self.identity)

    def __str__(self) -> str:
        return f"hole {self.identity}"


@dataclass(frozen=True, slots=True)
class WireIndicator:
    """One wire out of an ASTM E747 or ISO 19232 set."""

    number: int

    def __post_init__(self) -> None:
        if self.number not in _WIRE_DIAMETERS:
            raise ConfigurationError("no such wire number", number=self.number)

    @property
    def diameter(self) -> Length:
        return _WIRE_DIAMETERS[self.number]

    @property
    def iso_label(self) -> str:
        return f"W{self.number}"

    def sensitivity_at(self, penetrated: Length) -> Decimal:
        if penetrated.um <= 0:
            raise ValidationError("a penetrated thickness has to be positive")
        return self.diameter.ratio(penetrated) * 100

    def __str__(self) -> str:
        return self.iso_label


_HOLE_IDENTITIES: tuple[int, ...] = (
    5,
    7,
    10,
    12,
    15,
    17,
    20,
    25,
    30,
    35,
    40,
    45,
    50,
    60,
    70,
    80,
    100,
    120,
    140,
    160,
    200,
    240,
    280,
)

# ISO 19232-1 wire numbers and their diameters in millimetres
_WIRE_DIAMETERS: dict[int, Length] = {
    1: Length.from_mm("3.20"),
    2: Length.from_mm("2.50"),
    3: Length.from_mm("2.00"),
    4: Length.from_mm("1.60"),
    5: Length.from_mm("1.25"),
    6: Length.from_mm("1.00"),
    7: Length.from_mm("0.80"),
    8: Length.from_mm("0.63"),
    9: Length.from_mm("0.50"),
    10: Length.from_mm("0.40"),
    11: Length.from_mm("0.32"),
    12: Length.from_mm("0.25"),
    13: Length.from_mm("0.20"),
    14: Length.from_mm("0.16"),
    15: Length.from_mm("0.125"),
    16: Length.from_mm("0.10"),
    17: Length.from_mm("0.08"),
    18: Length.from_mm("0.063"),
    19: Length.from_mm("0.05"),
}

_WIRE_SETS: dict[str, tuple[int, ...]] = {
    "A": (1, 2, 3, 4, 5, 6, 7),
    "B": (6, 7, 8, 9, 10, 11, 12),
    "C": (10, 11, 12, 13, 14, 15, 16),
    "D": (13, 14, 15, 16, 17, 18, 19),
}


def hole_identities() -> tuple[int, ...]:
    return _HOLE_IDENTITIES


def wire_numbers() -> tuple[int, ...]:
    return tuple(sorted(_WIRE_DIAMETERS))


def wire_set(name: str) -> tuple[WireIndicator, ...]:
    try:
        numbers = _WIRE_SETS[name.upper()]
    except KeyError:
        raise ConfigurationError("no such wire set", name=name) from None
    return tuple(WireIndicator(n) for n in numbers)


def wire_sets_containing(number: int) -> tuple[str, ...]:
    """Which sets a crew could take to site and still have that wire."""
    return tuple(name for name, numbers in sorted(_WIRE_SETS.items()) if number in numbers)


def next_thicker_identity(identity: int) -> int:
    """The next plaque up, for when the essential hole will not show."""
    for candidate in _HOLE_IDENTITIES:
        if candidate > identity:
            return candidate
    raise ConfigurationError("nothing thicker is published", identity=identity)
