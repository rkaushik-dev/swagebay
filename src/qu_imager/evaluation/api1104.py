"""The API 1104 acceptance criteria, as a table rather than as a pile of ifs.

Section nine is the reason this package exists in the shape it does. Every
imperfection has its own rule, the rules are not the same shape as each other,
and several of them depend on the wall thickness rather than being fixed.

Four shapes of limit appear and a kind can carry any combination of them.

An individual limit caps one imperfection. Sometimes it is a length, sometimes
a width, and for burn through and porosity it is whichever of two numbers is
smaller, one of which is a fraction of the wall. That last form is the one that
gets hand coded wrong, because on thin wall the fraction wins and on heavy wall
the fixed number wins, and a shop that only ever sees one thickness never finds
out.

An aggregate limit caps the total in any continuous twelve inches. A count
limit caps how many there may be in the same window regardless of size. A short
weld limit replaces the window with a percentage of the weld.

Nothing here evaluates anything. This module is the transcription and it is
kept separate on purpose, so that a revision of the standard is a change to the
table and to nothing else.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length

WINDOW = Length.from_inch(12)
ACCUMULATION_AGGREGATE = Length.from_inch(2)
ACCUMULATION_FRACTION = Decimal("0.08")


@dataclass(frozen=True, slots=True)
class Criterion:
    """One row of section nine."""

    kind: Kind
    clause: str
    max_length: Length | None = None
    max_width: Length | None = None
    max_length_fraction_of_wall: Decimal | None = None
    max_aggregate: Length | None = None
    max_count: int | None = None
    short_weld_fraction: Decimal | None = None
    note: str = ""

    def individual_length_limit(self, wall: Length) -> Length | None:
        """The cap on one imperfection, with the wall fraction folded in.

        Where both a fixed length and a fraction of the wall are published the
        smaller of the two governs, which means the fraction binds on thin wall
        and the fixed number binds on heavy wall.
        """
        if wall.um <= 0:
            raise ValidationError("a wall thickness has to be positive")
        if self.max_length_fraction_of_wall is None:
            return self.max_length
        scaled = wall * self.max_length_fraction_of_wall
        if self.max_length is None:
            return scaled
        return min(self.max_length, scaled)


_CRITERIA: tuple[Criterion, ...] = (
    Criterion(
        kind=Kind.IP,
        clause="9.3.1",
        max_length=Length.from_inch(1),
        max_aggregate=Length.from_inch(1),
        short_weld_fraction=ACCUMULATION_FRACTION,
    ),
    Criterion(
        kind=Kind.IPD,
        clause="9.3.2",
        max_length=Length.from_inch(2),
        max_aggregate=Length.from_inch(3),
    ),
    Criterion(
        kind=Kind.ICP,
        clause="9.3.3",
        max_length=Length.from_inch(2),
        max_aggregate=Length.from_inch(2),
    ),
    Criterion(
        kind=Kind.IF,
        clause="9.3.4",
        max_length=Length.from_inch(1),
        max_aggregate=Length.from_inch(1),
        short_weld_fraction=ACCUMULATION_FRACTION,
    ),
    Criterion(
        kind=Kind.IFD,
        clause="9.3.5",
        max_length=Length.from_inch(2),
        max_aggregate=Length.from_inch(2),
        short_weld_fraction=ACCUMULATION_FRACTION,
    ),
    Criterion(
        kind=Kind.IC,
        clause="9.3.6",
        note="acceptable at any length while the density stays under the parent metal",
    ),
    Criterion(
        kind=Kind.BT,
        clause="9.3.7",
        max_length=Length.from_inch("0.5"),
        max_length_fraction_of_wall=Decimal("0.5"),
        max_aggregate=Length.from_inch("0.5"),
    ),
    Criterion(
        kind=Kind.ESI,
        clause="9.3.8",
        max_length=Length.from_inch(2),
        max_width=Length.from_mm("1.6"),
        max_aggregate=Length.from_inch(2),
    ),
    Criterion(
        kind=Kind.ISI,
        clause="9.3.8",
        max_width=Length.from_mm(3),
        max_aggregate=Length.from_inch("0.5"),
        max_count=4,
    ),
    Criterion(
        kind=Kind.P,
        clause="9.3.9",
        max_width=Length.from_mm(3),
        max_length_fraction_of_wall=Decimal("0.25"),
    ),
    Criterion(
        kind=Kind.CP,
        clause="9.3.9",
        max_length=Length.from_inch("0.5"),
        max_aggregate=Length.from_inch("0.5"),
    ),
    Criterion(
        kind=Kind.HB,
        clause="9.3.9",
        max_length=Length.from_inch("0.5"),
        max_aggregate=Length.from_inch(2),
    ),
    Criterion(
        kind=Kind.C,
        clause="9.3.10",
        max_length=Length.from_mm(4),
        note="only a shallow crater or star crack under four millimetres is acceptable",
    ),
    Criterion(
        kind=Kind.U,
        clause="9.3.11",
        max_length=Length.from_inch(2),
        max_aggregate=Length.from_inch(2),
        note="depth is capped at 0.8 mm or an eighth of the wall, whichever is smaller",
    ),
    Criterion(
        kind=Kind.AB,
        clause="9.3.12",
        note="an arc burn is removed and the area is examined again",
    ),
)

UNDERCUT_MAX_DEPTH = Length.from_mm("0.8")
UNDERCUT_DEPTH_FRACTION = Decimal("0.125")
UNDERCUT_ALWAYS_ACCEPTABLE = Length.from_mm("0.4")


def criterion(kind: Kind) -> Criterion:
    for row in _CRITERIA:
        if row.kind is kind:
            return row
    raise ConfigurationError("no api 1104 criterion for that kind", kind=kind.code)


def all_criteria() -> tuple[Criterion, ...]:
    return _CRITERIA


def undercut_depth_limit(wall: Length) -> Length:
    """Whichever of the two published depths is smaller at this wall."""
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")
    return min(UNDERCUT_MAX_DEPTH, wall * UNDERCUT_DEPTH_FRACTION)
