"""Film systems, their speed, and the density the code demands of them.

Film is not obsolete and the crews that say it is have not tried to get a
digital detector into a pipe rack in February. What matters about a film system
for planning is one number, the speed, because every exposure chart in the
office is drawn for one film and shooting a different one means scaling the
time. Doubling the speed halves the time, which is the whole of the arithmetic
and also the whole of the trap: a faster film is grainier, and the grain sets
the smallest flaw that can be seen at all.

After the shot, density is the gate. Too thin and there is no contrast to read.
Too dense and the viewer cannot push enough light through it. ASME puts the
floor at 1.8 for x-ray and 2.0 for gamma, and the ceiling at 4.0 for both, and
those numbers are hard. The variation rule is softer and more often missed:
through the body of the weld the density may not fall below 85 percent or rise
above 130 percent of the density read through the indicator.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ConfigurationError, ValidationError

DENSITY_FLOOR_XRAY = Decimal("1.8")
DENSITY_FLOOR_GAMMA = Decimal("2.0")
DENSITY_CEILING = Decimal("4.0")
DENSITY_FLOOR_COMPOSITE = Decimal("1.3")
VARIATION_LOW = Decimal("0.85")
VARIATION_HIGH = Decimal("1.30")


class FilmClass(Enum):
    """ASTM E1815 system classes, finest grain first."""

    SPECIAL = "special"
    CLASS_I = "I"
    CLASS_II = "II"
    CLASS_III = "III"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class FilmSystem:
    """A film and screen combination the shop stocks."""

    key: str
    name: str
    manufacturer: str
    film_class: FilmClass
    speed_index: Decimal
    """Relative speed with the reference film at 100. Bigger is faster."""
    granularity: Decimal
    """Root mean square granularity in optical density units at 2.0 D."""

    def __post_init__(self) -> None:
        if self.speed_index <= 0:
            raise ValidationError("a film speed index has to be positive", key=self.key)

    def time_factor_from(self, reference: FilmSystem) -> Decimal:
        """Multiply a chart time for the reference film to get this one."""
        return reference.speed_index / self.speed_index

    def is_finer_than(self, other: FilmSystem) -> bool:
        return self.granularity < other.granularity


_FILMS: dict[str, FilmSystem] = {
    f.key: f
    for f in [
        FilmSystem("d2", "Structurix D2", "Agfa", FilmClass.SPECIAL, Decimal(6), Decimal("0.012")),
        FilmSystem("d3", "Structurix D3", "Agfa", FilmClass.CLASS_I, Decimal(15), Decimal("0.014")),
        FilmSystem("d4", "Structurix D4", "Agfa", FilmClass.CLASS_I, Decimal(32), Decimal("0.017")),
        FilmSystem(
            "d5", "Structurix D5", "Agfa", FilmClass.CLASS_II, Decimal(55), Decimal("0.019")
        ),
        FilmSystem(
            "d7", "Structurix D7", "Agfa", FilmClass.CLASS_II, Decimal(100), Decimal("0.024")
        ),
        FilmSystem(
            "d8", "Structurix D8", "Agfa", FilmClass.CLASS_III, Decimal(180), Decimal("0.032")
        ),
        FilmSystem(
            "mx125",
            "Industrex MX125",
            "Carestream",
            FilmClass.CLASS_I,
            Decimal(20),
            Decimal("0.013"),
        ),
        FilmSystem(
            "t200", "Industrex T200", "Carestream", FilmClass.CLASS_I, Decimal(40), Decimal("0.018")
        ),
        FilmSystem(
            "aa400",
            "Industrex AA400",
            "Carestream",
            FilmClass.CLASS_II,
            Decimal(100),
            Decimal("0.023"),
        ),
        FilmSystem(
            "hs800",
            "Industrex HS800",
            "Carestream",
            FilmClass.CLASS_III,
            Decimal(200),
            Decimal("0.035"),
        ),
    ]
}

REFERENCE_FILM = _FILMS["d7"]
"""Every exposure chart on the wall is drawn for this one."""


def film(key: str) -> FilmSystem:
    try:
        return _FILMS[key]
    except KeyError:
        raise ConfigurationError("no such film system", key=key) from None


def known_films() -> tuple[str, ...]:
    return tuple(sorted(_FILMS))


def films_in_class(film_class: FilmClass) -> tuple[FilmSystem, ...]:
    fits = [f for f in _FILMS.values() if f.film_class is film_class]
    return tuple(sorted(fits, key=lambda f: f.speed_index))


def density_floor(gamma: bool, composite: bool = False) -> Decimal:
    """The minimum density the code will read, which depends on the source."""
    if composite:
        return DENSITY_FLOOR_COMPOSITE
    return DENSITY_FLOOR_GAMMA if gamma else DENSITY_FLOOR_XRAY


def density_in_range(density: Decimal, gamma: bool, composite: bool = False) -> bool:
    return density_floor(gamma, composite) <= density <= DENSITY_CEILING


def variation_window(through_indicator: Decimal) -> tuple[Decimal, Decimal]:
    """The band the density through the weld has to stay inside."""
    if through_indicator <= 0:
        raise ValidationError("a density reading has to be positive")
    return (through_indicator * VARIATION_LOW, through_indicator * VARIATION_HIGH)


def variation_acceptable(through_indicator: Decimal, through_weld: Decimal) -> bool:
    low, high = variation_window(through_indicator)
    return low <= through_weld <= high
