"""Which indicator goes on the shot, and what has to be visible on it.

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

The wire tables live here too, next to the hole table rather than in a module
of their own. They were separate while each was being written and the split
turned out to be along the wrong seam: a caller never wants one family in the
abstract, it wants whichever family the job specification names, and having to
know which module that lived in pushed that decision out to every caller. One
module, one entry point, and the specification decides.

The two families are still measured against different things. The hole table is
keyed on nominal single wall thickness. The wire tables are keyed on penetrated
thickness, which on a double wall shot is a very different number, and mixing
them up produces an indicator that is one or two rows too easy.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.indicators import (
    HoleIndicator,
    HoleSize,
    WireIndicator,
    wire_sets_containing,
)
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


class TechniqueClass(Enum):
    """ISO 17636-1 puts every job in one of two classes."""

    A = "A"
    B = "B"

    @property
    def is_improved(self) -> bool:
        return self is TechniqueClass.B

    def __str__(self) -> str:
        return f"class {self.value}"


# penetrated thickness ceiling in mm, wire number
_CLASS_A: tuple[tuple[str, int], ...] = (
    ("1.2", 18),
    ("2.0", 17),
    ("3.5", 16),
    ("5.0", 15),
    ("7.0", 14),
    ("10", 13),
    ("15", 12),
    ("25", 11),
    ("32", 10),
    ("40", 9),
    ("55", 8),
    ("85", 7),
    ("150", 6),
    ("200", 5),
    ("250", 4),
)

_CLASS_B: tuple[tuple[str, int], ...] = (
    ("1.5", 19),
    ("2.5", 18),
    ("4.0", 17),
    ("6.0", 16),
    ("8.0", 15),
    ("12", 14),
    ("20", 13),
    ("30", 12),
    ("35", 11),
    ("45", 10),
    ("65", 9),
    ("120", 8),
    ("200", 7),
    ("350", 6),
)


@dataclass(frozen=True, slots=True)
class WireSelection:
    """The wire the standard demands, and where to get one."""

    penetrated: Length
    technique_class: TechniqueClass
    wire: WireIndicator
    available_sets: tuple[str, ...]

    def describe(self) -> str:
        sets = "/".join(self.available_sets) if self.available_sets else "no stock set"
        return f"{self.wire.iso_label} {self.technique_class}, set {sets}"


def _table(technique_class: TechniqueClass) -> tuple[tuple[str, int], ...]:
    return _CLASS_B if technique_class.is_improved else _CLASS_A


def wire_selection(penetrated: Length, technique_class: TechniqueClass) -> WireSelection:
    """Read the class table on penetrated thickness."""
    if penetrated.um <= 0:
        raise ValidationError("a penetrated thickness has to be positive")
    for ceiling, number in _table(technique_class):
        if penetrated <= Length.from_mm(ceiling):
            return WireSelection(
                penetrated=penetrated,
                technique_class=technique_class,
                wire=WireIndicator(number),
                available_sets=wire_sets_containing(number),
            )
    raise TechniqueError(
        "that penetrated thickness is past the end of the wire table",
        penetrated_mm=str(penetrated.mm),
        technique_class=technique_class.value,
    )


def class_b_is_stricter(penetrated: Length) -> bool:
    """Whether the improved class actually asks for a finer wire here."""
    basic = wire_selection(penetrated, TechniqueClass.A)
    improved = wire_selection(penetrated, TechniqueClass.B)
    return improved.wire.diameter < basic.wire.diameter


def table_ceiling(technique_class: TechniqueClass) -> Length:
    return Length.from_mm(_table(technique_class)[-1][0])


def bands(technique_class: TechniqueClass) -> tuple[tuple[Length, int], ...]:
    return tuple((Length.from_mm(c), n) for c, n in _table(technique_class))


class Family(Enum):
    """Which indicator family the job specification calls for."""

    HOLE = "hole type"
    WIRE = "wire type"

    @property
    def measured_on_penetrated_thickness(self) -> bool:
        """The wire tables read penetrated thickness. The hole table reads the wall."""
        return self is Family.WIRE

    def __str__(self) -> str:
        return self.value


def select_for(
    family: Family,
    wall: Length,
    penetrated: Length,
    side: IndicatorSide = IndicatorSide.SOURCE,
    technique_class: TechniqueClass = TechniqueClass.A,
) -> HoleSelection | WireSelection:
    """One entry point, reading whichever table the family names.

    Both thicknesses are taken rather than one, so the caller cannot hand the
    wrong one in. Which of them is used is this function's business, and it is
    the whole reason the two tables were worth putting in one module.
    """
    if family is Family.HOLE:
        return hole_selection(wall, side)
    return wire_selection(penetrated, technique_class)
