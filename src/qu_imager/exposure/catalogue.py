"""The charts this shop actually owns.

Six curves, and the shop has been shooting off them for years. They are not
derived from anything. They were established by shooting step wedges, and every
one of them was re established the last time the processor chemistry changed,
which is why the density each was drawn at is part of its identity.

The resolver is deliberately narrow. Ask for an isotope, a material, a film and
a density and either a chart exists or it does not. There is no nearest match
and no averaging of two curves, because a chart is an empirical object and the
average of two empirical objects is not one.

What the resolver does allow is a chart drawn for a different film, returned
with the film it was drawn for named, so the caller can apply the speed factor
knowingly. That is a correction the trade makes every day and it is sound. What
it will not do is silently substitute a density, because density corrections
are where the argument with the client starts.
"""

from __future__ import annotations

from decimal import Decimal

from qu_imager.errors import ConfigurationError
from qu_imager.exposure.chart import ExposureChart, chart_from_inches

_IR192_STEEL_D7 = chart_from_inches(
    "ir192-steel-d7-2.0",
    "ir192",
    "carbon_steel",
    "d7",
    "2.0",
    "24",
    (
        ("0.25", "4"),
        ("0.375", "5.6"),
        ("0.50", "8"),
        ("0.625", "11"),
        ("0.75", "16"),
        ("0.875", "22"),
        ("1.00", "30"),
        ("1.25", "55"),
        ("1.50", "100"),
        ("1.75", "180"),
        ("2.00", "320"),
        ("2.25", "580"),
        ("2.50", "1050"),
    ),
)

_IR192_STEEL_D5 = chart_from_inches(
    "ir192-steel-d5-2.0",
    "ir192",
    "carbon_steel",
    "d5",
    "2.0",
    "24",
    (
        ("0.25", "7.3"),
        ("0.50", "14.5"),
        ("0.75", "29"),
        ("1.00", "55"),
        ("1.25", "100"),
        ("1.50", "182"),
        ("1.75", "327"),
        ("2.00", "582"),
    ),
)

_SE75_STEEL_D7 = chart_from_inches(
    "se75-steel-d7-2.0",
    "se75",
    "carbon_steel",
    "d7",
    "2.0",
    "24",
    (
        ("0.16", "6"),
        ("0.25", "11"),
        ("0.375", "22"),
        ("0.50", "44"),
        ("0.625", "88"),
        ("0.75", "175"),
        ("0.875", "350"),
        ("1.00", "700"),
    ),
)

_CO60_STEEL_D7 = chart_from_inches(
    "co60-steel-d7-2.0",
    "co60",
    "carbon_steel",
    "d7",
    "2.0",
    "36",
    (
        ("2.00", "60"),
        ("2.50", "88"),
        ("3.00", "130"),
        ("3.50", "190"),
        ("4.00", "280"),
        ("5.00", "600"),
        ("6.00", "1300"),
        ("7.00", "2800"),
        ("8.00", "6000"),
    ),
)

_IR192_ALUMINIUM_D7 = chart_from_inches(
    "ir192-aluminium-d7-2.0",
    "ir192",
    "aluminium",
    "d7",
    "2.0",
    "24",
    (
        ("0.50", "3.2"),
        ("1.00", "5.4"),
        ("1.50", "9.1"),
        ("2.00", "15.4"),
        ("3.00", "44"),
        ("4.00", "125"),
    ),
)

_IR192_STEEL_D7_HIGH = chart_from_inches(
    "ir192-steel-d7-2.5",
    "ir192",
    "carbon_steel",
    "d7",
    "2.5",
    "24",
    (
        ("0.25", "5.4"),
        ("0.50", "10.8"),
        ("0.75", "21.6"),
        ("1.00", "40.5"),
        ("1.25", "74"),
        ("1.50", "135"),
        ("1.75", "243"),
        ("2.00", "432"),
    ),
)

_CHARTS: tuple[ExposureChart, ...] = (
    _IR192_STEEL_D7,
    _IR192_STEEL_D5,
    _IR192_STEEL_D7_HIGH,
    _SE75_STEEL_D7,
    _CO60_STEEL_D7,
    _IR192_ALUMINIUM_D7,
)


def all_charts() -> tuple[ExposureChart, ...]:
    return _CHARTS


def chart_keys() -> tuple[str, ...]:
    return tuple(sorted(c.key for c in _CHARTS))


def chart(key: str) -> ExposureChart:
    for candidate in _CHARTS:
        if candidate.key == key:
            return candidate
    raise ConfigurationError("no such exposure chart", key=key)


def chart_for(
    isotope_key: str, material_key: str, film_key: str, density: Decimal
) -> ExposureChart:
    """The exact chart, or a refusal."""
    for candidate in _CHARTS:
        if (
            candidate.isotope_key == isotope_key
            and candidate.material_key == material_key
            and candidate.film_key == film_key
            and candidate.density == density
        ):
            return candidate
    raise ConfigurationError(
        "no chart has been established for that combination",
        isotope=isotope_key,
        material=material_key,
        film=film_key,
        density=str(density),
    )


def charts_for_film_substitution(
    isotope_key: str, material_key: str, density: Decimal
) -> tuple[ExposureChart, ...]:
    """Charts that match everything but the film, so a speed factor can be used."""
    return tuple(
        c
        for c in _CHARTS
        if c.isotope_key == isotope_key and c.material_key == material_key and c.density == density
    )
