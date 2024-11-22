"""Choosing a kilovoltage, which is a choice and not a lookup.

There is a table, and the table is the starting point rather than the answer.
It says the lowest kilovoltage that will get through a given thickness of steel
in a reasonable time, and the reason to start low is contrast: a soft beam is
selective about what it passes, so a flaw shows as a bigger difference in
density than the same flaw under a hard beam.

Three things push the choice up from the table value and they are all real.

A thick part shot at the table value takes a very long time, and at some point
the exposure runs past the window or past what the set will sit at, so the
kilovoltage goes up and the contrast goes down. That is a trade the shooter
makes knowingly.

A high density requirement pushes it up the same way, because a denser film
needs more exposure.

A client specification can cap it. Several do, precisely to protect contrast,
and a cap that bites means the job takes longer rather than the kilovoltage
going up, which is the client having decided the trade in advance.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.length import Length
from qu_imager.sources.xray import Tube

# steel thickness ceiling in millimetres, lowest workable kilovoltage
_STEEL_KV: tuple[tuple[str, int], ...] = (
    ("3", 90),
    ("6", 120),
    ("10", 140),
    ("13", 150),
    ("19", 180),
    ("25", 200),
    ("32", 250),
    ("38", 280),
    ("50", 320),
    ("65", 400),
    ("80", 450),
)

CONTRAST_STEP = 20
"""How far the kilovoltage moves when it has to move at all."""


@dataclass(frozen=True, slots=True)
class Choice:
    """A kilovoltage, with why it is not the table value."""

    kv: int
    from_table: int
    reasons: tuple[str, ...]

    @property
    def is_table_value(self) -> bool:
        return self.kv == self.from_table

    @property
    def steps_above_table(self) -> int:
        return (self.kv - self.from_table) // CONTRAST_STEP

    def describe(self) -> str:
        if self.is_table_value:
            return f"{self.kv} kV"
        return f"{self.kv} kV, up from {self.from_table}: " + "; ".join(self.reasons)


def lowest_workable(steel_thickness: Length) -> int:
    """The table value, which is where every choice starts."""
    if steel_thickness.um <= 0:
        raise ValidationError("a thickness has to be positive")
    for ceiling, kv in _STEEL_KV:
        if steel_thickness <= Length.from_mm(ceiling):
            return kv
    raise TechniqueError(
        "that thickness is past the end of the kilovoltage table",
        thickness_mm=str(steel_thickness.mm),
        table_ends_at_mm="80",
    )


def choose(
    steel_thickness: Length,
    tube: Tube,
    density: Decimal = Decimal("2.0"),
    ceiling_kv: int | None = None,
) -> Choice:
    """Start at the table and push up only for a reason that is written down."""
    table = lowest_workable(steel_thickness)
    kv = table
    reasons: list[str] = []

    if kv < tube.min_kv:
        kv = tube.min_kv
        reasons.append(f"the set will not go below {tube.min_kv}")
    if density >= Decimal("2.5"):
        kv += CONTRAST_STEP
        reasons.append(f"density {density} needs more exposure")
    if kv > tube.max_kv:
        raise TechniqueError(
            "the set will not reach the kilovoltage this thickness needs",
            needed=kv,
            max_kv=tube.max_kv,
            tube=tube.key,
        )
    if ceiling_kv is not None:
        if ceiling_kv < table:
            raise TechniqueError(
                "the specification caps the kilovoltage below what will penetrate",
                cap=ceiling_kv,
                needed=table,
            )
        if kv > ceiling_kv:
            kv = ceiling_kv
            reasons.append(f"the specification caps it at {ceiling_kv}")
    return Choice(kv=kv, from_table=table, reasons=tuple(reasons))


def contrast_penalty(choice: Choice) -> Decimal:
    """A rough index of what was given up, for a note on the sheet.

    Every step above the table value costs contrast. The number is not a
    measurement of anything and it does not pretend to be. It exists so that
    two techniques for the same weld can be compared on more than exposure
    time, and so that a sheet three steps up carries a visible mark.
    """
    if choice.steps_above_table <= 0:
        return Decimal(0)
    return Decimal(choice.steps_above_table) / 10


def table_bands() -> tuple[tuple[Length, int], ...]:
    return tuple((Length.from_mm(c), kv) for c, kv in _STEEL_KV)
