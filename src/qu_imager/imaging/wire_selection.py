"""Which wire has to be visible, on the european side of the file.

Clients that write to ISO rather than ASME ask for wire indicators and they ask
in two classes. Class A is the basic technique. Class B is the improved
technique and it demands one wire finer in every band, which in practice means
a slower film, a longer distance or both. A job specification that says class B
and a shooter who reads the class A row produces a radiograph that will be
rejected on review, months later, by somebody with a copy of the standard.

The lookup is on penetrated thickness, not nominal wall, which is the other
half of the trap. A double wall shot penetrates two walls and a cap, so it
lands two or three bands further down the table than the wall thickness alone
would suggest, and the wire it needs is coarser as a result. That is not a
concession, it is the standard admitting the shot is harder.

The bands here run the other way from the hole type table: they are open at the
bottom and closed at the top, and the number goes up as the wire gets thinner.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.indicators import WireIndicator, wire_sets_containing
from qu_imager.quantities.length import Length


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
