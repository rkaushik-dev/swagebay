"""Nominal pipe size and wall schedule, from ASME B36.10M.

A work order says six inch schedule forty. It does not say 168.3 mm outside
diameter and 7.11 mm wall, and the shooter is not going to look it up on a
phone at four in the morning in a lay down yard. This is the table.

Two things about nominal size trip people up and both are in the tests. Nominal
is not a measurement: a two inch line is 60.3 mm across, which is not two
inches of anything. And outside diameter is fixed for a given nominal size no
matter what the schedule is, which is the whole point of the schedule system,
so a heavier wall eats the bore rather than growing the pipe.

Only the schedules the shop actually meets are in here. A line list that asks
for something else gets a refusal and somebody enters the wall by hand, which
is better than a wall that was interpolated out of nowhere.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.materials import Material
from qu_imager.quantities.length import Length

SCHEDULES = ("STD", "XS", "XXS", "40", "80", "160")

# nominal size -> outside diameter mm, then wall mm by schedule
_TABLE: dict[str, tuple[str, dict[str, str]]] = {
    "1/2": (
        "21.3",
        {"STD": "2.77", "XS": "3.73", "XXS": "7.47", "40": "2.77", "80": "3.73", "160": "4.78"},
    ),
    "3/4": (
        "26.7",
        {"STD": "2.87", "XS": "3.91", "XXS": "7.82", "40": "2.87", "80": "3.91", "160": "5.56"},
    ),
    "1": (
        "33.4",
        {"STD": "3.38", "XS": "4.55", "XXS": "9.09", "40": "3.38", "80": "4.55", "160": "6.35"},
    ),
    "1-1/4": (
        "42.2",
        {"STD": "3.56", "XS": "4.85", "XXS": "9.70", "40": "3.56", "80": "4.85", "160": "6.35"},
    ),
    "1-1/2": (
        "48.3",
        {"STD": "3.68", "XS": "5.08", "XXS": "10.15", "40": "3.68", "80": "5.08", "160": "7.14"},
    ),
    "2": (
        "60.3",
        {"STD": "3.91", "XS": "5.54", "XXS": "11.07", "40": "3.91", "80": "5.54", "160": "8.74"},
    ),
    "2-1/2": (
        "73.0",
        {"STD": "5.16", "XS": "7.01", "XXS": "14.02", "40": "5.16", "80": "7.01", "160": "9.53"},
    ),
    "3": (
        "88.9",
        {"STD": "5.49", "XS": "7.62", "XXS": "15.24", "40": "5.49", "80": "7.62", "160": "11.13"},
    ),
    "4": (
        "114.3",
        {"STD": "6.02", "XS": "8.56", "XXS": "17.12", "40": "6.02", "80": "8.56", "160": "13.49"},
    ),
    "5": (
        "141.3",
        {"STD": "6.55", "XS": "9.53", "XXS": "19.05", "40": "6.55", "80": "9.53", "160": "15.88"},
    ),
    "6": (
        "168.3",
        {"STD": "7.11", "XS": "10.97", "XXS": "21.95", "40": "7.11", "80": "10.97", "160": "18.26"},
    ),
    "8": (
        "219.1",
        {"STD": "8.18", "XS": "12.70", "XXS": "22.23", "40": "8.18", "80": "12.70", "160": "23.01"},
    ),
    "10": (
        "273.0",
        {"STD": "9.27", "XS": "12.70", "XXS": "25.40", "40": "9.27", "80": "15.09", "160": "28.58"},
    ),
    "12": (
        "323.8",
        {
            "STD": "9.53",
            "XS": "12.70",
            "XXS": "25.40",
            "40": "10.31",
            "80": "17.48",
            "160": "33.32",
        },
    ),
    "14": ("355.6", {"STD": "9.53", "XS": "12.70", "40": "11.13", "80": "19.05", "160": "35.71"}),
    "16": ("406.4", {"STD": "9.53", "XS": "12.70", "40": "12.70", "80": "21.44", "160": "40.49"}),
    "18": ("457.2", {"STD": "9.53", "XS": "12.70", "40": "14.27", "80": "23.83", "160": "45.24"}),
    "20": ("508.0", {"STD": "9.53", "XS": "12.70", "40": "15.09", "80": "26.19", "160": "50.01"}),
    "24": ("609.6", {"STD": "9.53", "XS": "12.70", "40": "17.48", "80": "30.96", "160": "59.54"}),
}


@dataclass(frozen=True, slots=True)
class PipeSize:
    """One row of the table, resolved."""

    nominal: str
    outside_diameter: Length
    schedule: str
    wall: Length

    @property
    def inside_diameter(self) -> Length:
        return self.outside_diameter - self.wall * 2

    def __str__(self) -> str:
        return f"NPS {self.nominal} sch {self.schedule}"


def nominal_sizes() -> tuple[str, ...]:
    return tuple(_TABLE)


def outside_diameter(nominal: str) -> Length:
    try:
        return Length.from_mm(_TABLE[nominal][0])
    except KeyError:
        raise ConfigurationError("no such nominal pipe size", nominal=nominal) from None


def wall(nominal: str, schedule: str) -> Length:
    try:
        walls = _TABLE[nominal][1]
    except KeyError:
        raise ConfigurationError("no such nominal pipe size", nominal=nominal) from None
    key = schedule.upper()
    try:
        return Length.from_mm(walls[key])
    except KeyError:
        raise ConfigurationError(
            "that schedule is not published at this size", nominal=nominal, schedule=schedule
        ) from None


def resolve(nominal: str, schedule: str) -> PipeSize:
    return PipeSize(
        nominal=nominal,
        outside_diameter=outside_diameter(nominal),
        schedule=schedule.upper(),
        wall=wall(nominal, schedule),
    )


def girth_weld(
    nominal: str,
    schedule: str,
    material: Material,
    reinforcement: Length = Length.ZERO,
) -> Joint:
    """The joint a line list line turns into."""
    size = resolve(nominal, schedule)
    return Joint(
        form=JointForm.PIPE_GIRTH,
        wall=size.wall,
        material=material,
        outside_diameter=size.outside_diameter,
        reinforcement=reinforcement,
    )


def nearest_nominal(measured: Length, tolerance: Decimal = Decimal("0.03")) -> str:
    """Work back from a tape measure to a nominal size, within a tolerance."""
    best: tuple[Decimal, str] | None = None
    for nominal, (od, _walls) in _TABLE.items():
        published = Length.from_mm(od)
        if published.um == 0:
            continue
        error = abs(Decimal(measured.um - published.um) / Decimal(published.um))
        if best is None or error < best[0]:
            best = (error, nominal)
    if best is None or best[0] > tolerance:
        raise ConfigurationError(
            "no nominal size is close enough to that measurement", mm=str(measured.mm)
        )
    return best[1]
