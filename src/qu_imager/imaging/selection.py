"""Which indicator goes on the shot, and which hole has to show.

This is a lookup and it is the single most consulted table in the trade. Take
the nominal single wall thickness, find the band it falls in, read off the
plaque number and the essential hole. Two columns, because an indicator on the
source side of the part is further from the film and its shadow is bigger and
softer than the same plaque lying on the film side, so the film side column is
one row easier all the way down.

The bands are half open at the bottom and closed at the top: over a quarter
inch through three eighths. A weld exactly on a boundary takes the lower band,
which is the stricter one, and getting that backwards is the classic way to
ship a radiograph with the wrong penetrameter on it.

Source side is the default and film side needs a reason. A plaque on the film
side has to be marked with a lead F beside it, and a radiograph with an F on it
and no note explaining why the source side was inaccessible is a finding
waiting to happen.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.indicators import HoleIndicator, HoleSize
from qu_imager.quantities.length import Length


class IndicatorSide(Enum):
    SOURCE = "source side"
    FILM = "film side"

    @property
    def needs_marking(self) -> bool:
        """A film side indicator carries a lead F next to it."""
        return self is IndicatorSide.FILM

    def __str__(self) -> str:
        return self.value


# nominal single wall thickness ceiling in inches, source side plaque, film side plaque
_HOLE_TABLE: tuple[tuple[str, int, int], ...] = (
    ("0.25", 12, 10),
    ("0.375", 15, 12),
    ("0.50", 17, 15),
    ("0.75", 20, 17),
    ("1.00", 25, 20),
    ("1.50", 30, 25),
    ("2.00", 35, 30),
    ("2.50", 40, 35),
    ("4.00", 50, 40),
    ("6.00", 60, 50),
    ("8.00", 80, 60),
    ("10.00", 100, 80),
    ("12.00", 120, 100),
    ("16.00", 160, 120),
    ("20.00", 200, 160),
)

ESSENTIAL_HOLE = HoleSize.TWO_T
"""Every band in the table calls for the 2T hole. The column exists anyway."""


@dataclass(frozen=True, slots=True)
class HoleSelection:
    """What the table says for one thickness on one side."""

    thickness: Length
    side: IndicatorSide
    indicator: HoleIndicator
    hole: HoleSize

    @property
    def needs_f_marker(self) -> bool:
        return self.side.needs_marking

    def describe(self) -> str:
        marker = " with an F marker" if self.needs_f_marker else ""
        return f"{self.indicator.label()} {self.hole} on the {self.side}{marker}"


def hole_selection(thickness: Length, side: IndicatorSide) -> HoleSelection:
    """Read the table. Bands are closed at the top, so a boundary reads down."""
    if thickness.um <= 0:
        raise ValidationError("a nominal thickness has to be positive")
    for ceiling, source_side, film_side in _HOLE_TABLE:
        if thickness <= Length.from_inch(ceiling):
            identity = source_side if side is IndicatorSide.SOURCE else film_side
            return HoleSelection(
                thickness=thickness,
                side=side,
                indicator=HoleIndicator(identity),
                hole=ESSENTIAL_HOLE,
            )
    raise TechniqueError(
        "that thickness is past the end of the indicator table",
        thickness_mm=str(thickness.mm),
        table_ends_at_in="20.00",
    )


def band_ceiling(thickness: Length) -> Length:
    """The top of the band a thickness falls in, for showing the working."""
    for ceiling, _source_side, _film_side in _HOLE_TABLE:
        top = Length.from_inch(ceiling)
        if thickness <= top:
            return top
    raise TechniqueError("that thickness is past the end of the indicator table")


def is_boundary_thickness(thickness: Length) -> bool:
    """Whether a thickness sits exactly on a band edge, which reads down."""
    return any(thickness == Length.from_inch(c) for c, _s, _f in _HOLE_TABLE)


def table_bands() -> tuple[tuple[Length, int, int], ...]:
    return tuple(
        (Length.from_inch(c), source_side, film_side) for c, source_side, film_side in _HOLE_TABLE
    )
