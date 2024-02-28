"""The isotopes the shop shoots with.

Four numbers decide whether an isotope is the right one for a weld. The half
life says how long a source stays useful and therefore what it costs to own.
The gamma constant says how hot it is at a metre, which is the whole of the
safety calculation. The energy says how well it penetrates. The practical
thickness range is the trade answer to all three together, and it is narrower
than the physics allows because outside it the contrast is not good enough to
find anything.

Iridium is the workhorse and it covers most of what a pipeline crew meets.
Selenium is softer and is what gets used on thin wall where iridium washes the
contrast out. Cobalt is for heavy vessel wall and it needs a shielded area
nobody has on a construction site. Ytterbium is for very thin wall and small
bore and hardly anybody owns one.

Note the hole. ASTM E94 publishes equivalence factor columns for iridium and
cobalt and for x-ray qualities, and for nothing else. Selenium and ytterbium
have no column, so this module reports no equivalence quality for them rather
than borrowing a neighbour, and the caller has to decide what to do about it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError
from qu_imager.materials import Quality
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Isotope:
    """A gamma emitter, with what planning and safety both need from it."""

    key: str
    name: str
    symbol: str
    half_life_days: Decimal
    gamma_constant: Decimal
    """Roentgen an hour at one metre from one curie."""
    energy_kev_low: int
    energy_kev_high: int
    steel_min: Length
    steel_max: Length
    equivalence_quality: Quality | None

    @property
    def half_life(self) -> Duration:
        return Duration.from_hours(self.half_life_days * 24)

    @property
    def is_broad_spectrum(self) -> bool:
        """Cobalt is two clean lines. The rest are a spread."""
        return self.energy_kev_high - self.energy_kev_low > 200

    def covers(self, steel_thickness: Length) -> bool:
        """Whether this isotope is the trade answer for that wall."""
        return self.steel_min <= steel_thickness <= self.steel_max

    def quality_for_equivalence(self) -> Quality:
        """The E94 column to read, or a refusal if the standard has none."""
        if self.equivalence_quality is None:
            raise ConfigurationError(
                "astm e94 publishes no equivalence column for this isotope",
                isotope=self.key,
            )
        return self.equivalence_quality

    def __str__(self) -> str:
        return self.symbol


_ISOTOPES: dict[str, Isotope] = {
    i.key: i
    for i in [
        Isotope(
            key="ir192",
            name="iridium 192",
            symbol="Ir-192",
            half_life_days=Decimal("73.83"),
            gamma_constant=Decimal("0.48"),
            energy_kev_low=206,
            energy_kev_high=612,
            steel_min=Length.from_mm(12),
            steel_max=Length.from_mm(63),
            equivalence_quality=Quality.IR192,
        ),
        Isotope(
            key="se75",
            name="selenium 75",
            symbol="Se-75",
            half_life_days=Decimal("119.78"),
            gamma_constant=Decimal("0.203"),
            energy_kev_low=66,
            energy_kev_high=401,
            steel_min=Length.from_mm(4),
            steel_max=Length.from_mm(28),
            equivalence_quality=None,
        ),
        Isotope(
            key="co60",
            name="cobalt 60",
            symbol="Co-60",
            half_life_days=Decimal("1925.3"),
            gamma_constant=Decimal("1.32"),
            energy_kev_low=1173,
            energy_kev_high=1333,
            steel_min=Length.from_mm(50),
            steel_max=Length.from_mm(200),
            equivalence_quality=Quality.CO60,
        ),
        Isotope(
            key="yb169",
            name="ytterbium 169",
            symbol="Yb-169",
            half_life_days=Decimal("32.0"),
            gamma_constant=Decimal("0.125"),
            energy_kev_low=63,
            energy_kev_high=308,
            steel_min=Length.from_mm(1),
            steel_max=Length.from_mm(15),
            equivalence_quality=None,
        ),
        Isotope(
            key="cs137",
            name="caesium 137",
            symbol="Cs-137",
            half_life_days=Decimal("11012.0"),
            gamma_constant=Decimal("0.33"),
            energy_kev_low=662,
            energy_kev_high=662,
            steel_min=Length.from_mm(25),
            steel_max=Length.from_mm(100),
            equivalence_quality=None,
        ),
    ]
}


def isotope(key: str) -> Isotope:
    try:
        return _ISOTOPES[key]
    except KeyError:
        raise ConfigurationError("no such isotope", key=key) from None


def known_isotopes() -> tuple[str, ...]:
    return tuple(sorted(_ISOTOPES))


def isotopes_covering(steel_thickness: Length) -> tuple[Isotope, ...]:
    """Every isotope whose practical range takes that wall, softest first."""
    fits = [i for i in _ISOTOPES.values() if i.covers(steel_thickness)]
    return tuple(sorted(fits, key=lambda i: i.energy_kev_high))


IR192 = _ISOTOPES["ir192"]
SE75 = _ISOTOPES["se75"]
CO60 = _ISOTOPES["co60"]
