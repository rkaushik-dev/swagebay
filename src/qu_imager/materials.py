"""Materials, and the factor that turns a thickness of one into a thickness of steel.

Every exposure chart in the office is drawn for carbon steel. When the weld is
aluminium or inconel the chart is still the one on the wall, so the thickness
gets converted first: multiply by the radiographic equivalence factor for the
material and the radiation quality, read the chart at that steel thickness, and
shoot. Two inches of aluminium at 150 kV behaves like a quarter inch of steel,
which is why an aluminium job takes a fraction of the time.

The factors are the ASTM E94 table. They are approximate by construction, they
are a function of the beam quality and not just the isotope, and the table has
holes in it where a combination is not used in practice. The holes are real and
this module reports them rather than filling them in with a guess.

Densities are here too because the safety side needs them for shielding, but
density does not decide attenuation on its own and nothing derives an
equivalence factor from one.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.length import Length


class Quality(Enum):
    """A beam hard enough to be a column in the equivalence table."""

    KV100 = "100 kV"
    KV150 = "150 kV"
    KV220 = "220 kV"
    KV400 = "400 kV"
    MV1 = "1 MV"
    MV2 = "2 MV"
    MV4_25 = "4 to 25 MV"
    IR192 = "Ir-192"
    CO60 = "Co-60"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Material:
    """A material the shop shoots, with what is needed to plan an exposure."""

    key: str
    name: str
    density_kg_m3: int
    factors: dict[Quality, Decimal]

    def factor_at(self, quality: Quality) -> Decimal:
        """The equivalence factor, or a refusal if the table has a hole there."""
        try:
            return self.factors[quality]
        except KeyError:
            raise ConfigurationError(
                "no radiographic equivalence factor is published for this combination",
                material=self.key,
                quality=quality.value,
            ) from None

    def steel_equivalent(self, thickness: Length, quality: Quality) -> Length:
        """The steel thickness that attenuates like this thickness of this material."""
        if thickness.um < 0:
            raise ValidationError("a thickness cannot be negative", um=thickness.um)
        return thickness * self.factor_at(quality)

    def is_ferrous(self) -> bool:
        return self.key in {"carbon_steel", "stainless_304", "stainless_316"}

    def __str__(self) -> str:
        return self.name


def _f(pairs: dict[str, str]) -> dict[Quality, Decimal]:
    return {Quality[k]: Decimal(v) for k, v in pairs.items()}


_MATERIALS: dict[str, Material] = {
    m.key: m
    for m in [
        Material(
            "magnesium",
            "magnesium",
            1740,
            _f({"KV100": "0.05", "KV150": "0.05", "KV220": "0.08"}),
        ),
        Material(
            "aluminium",
            "aluminium",
            2700,
            _f(
                {
                    "KV100": "0.08",
                    "KV150": "0.12",
                    "KV220": "0.18",
                    "IR192": "0.35",
                    "CO60": "0.35",
                }
            ),
        ),
        Material(
            "aluminium_2024",
            "aluminium 2024",
            2780,
            _f(
                {
                    "KV100": "0.10",
                    "KV150": "0.14",
                    "KV220": "0.18",
                    "IR192": "0.35",
                    "CO60": "0.35",
                }
            ),
        ),
        Material(
            "titanium",
            "titanium",
            4510,
            _f(
                {
                    "KV150": "0.54",
                    "KV220": "0.54",
                    "KV400": "0.71",
                    "MV1": "0.9",
                    "MV2": "0.9",
                    "MV4_25": "0.9",
                    "IR192": "0.9",
                    "CO60": "0.9",
                }
            ),
        ),
        Material("carbon_steel", "carbon steel", 7850, _f({q.name: "1.0" for q in Quality})),
        Material("stainless_304", "304 stainless", 8000, _f({q.name: "1.0" for q in Quality})),
        Material("stainless_316", "316 stainless", 8000, _f({q.name: "1.0" for q in Quality})),
        Material(
            "copper",
            "copper",
            8960,
            _f(
                {
                    "KV100": "1.5",
                    "KV150": "1.6",
                    "KV220": "1.4",
                    "KV400": "1.4",
                    "MV1": "1.1",
                    "MV2": "1.1",
                    "MV4_25": "1.0",
                    "IR192": "1.1",
                    "CO60": "1.1",
                }
            ),
        ),
        Material(
            "brass",
            "brass",
            8500,
            _f(
                {
                    "KV150": "1.4",
                    "KV220": "1.3",
                    "KV400": "1.3",
                    "MV1": "1.2",
                    "MV2": "1.2",
                    "IR192": "1.1",
                    "CO60": "1.0",
                }
            ),
        ),
        Material(
            "inconel",
            "inconel X",
            8440,
            _f(
                {
                    "KV100": "1.4",
                    "KV150": "1.3",
                    "KV220": "1.3",
                    "KV400": "1.3",
                    "MV1": "1.3",
                    "MV2": "1.3",
                    "MV4_25": "1.3",
                    "IR192": "1.3",
                    "CO60": "1.3",
                }
            ),
        ),
        Material(
            "zirconium",
            "zirconium",
            6520,
            _f(
                {
                    "KV100": "2.4",
                    "KV150": "2.3",
                    "KV220": "2.0",
                    "KV400": "1.7",
                    "MV1": "1.5",
                    "MV2": "1.0",
                    "MV4_25": "1.0",
                    "IR192": "1.2",
                    "CO60": "1.0",
                }
            ),
        ),
        Material(
            "lead",
            "lead",
            11340,
            _f(
                {
                    "KV100": "14.0",
                    "KV150": "14.0",
                    "KV220": "12.0",
                    "MV1": "5.0",
                    "MV2": "2.5",
                    "MV4_25": "3.0",
                    "IR192": "4.0",
                    "CO60": "2.3",
                }
            ),
        ),
    ]
}


def material(key: str) -> Material:
    """Look a material up by its key, refusing anything not in the file."""
    try:
        return _MATERIALS[key]
    except KeyError:
        raise ConfigurationError("no such material", key=key) from None


def known_materials() -> tuple[str, ...]:
    return tuple(sorted(_MATERIALS))


STEEL = _MATERIALS["carbon_steel"]
