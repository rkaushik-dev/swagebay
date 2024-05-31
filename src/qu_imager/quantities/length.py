"""Lengths, held as whole micrometres.

The trade measures in two systems at once and refuses to pick one. A wall is
0.375 inch on the drawing and 9.53 mm on the mill certificate. A wire indicator
is 0.16 mm. A source to film distance is 24 inches or 610 mm depending on who
wrote the procedure. Floating point millimetres lose the round inch values and
floating point inches lose the round millimetre ones, so neither is the store.

Micrometres are. Every value the trade quotes is a whole number of micrometres:
one thousandth of an inch is 25.4 um exactly, and no standard quotes anything
finer than 0.01 mm. Arithmetic stays exact, equality means equality, and a
sorted list of wire diameters sorts the way the gauge card does.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import ClassVar

from qu_imager.errors import ValidationError

_UM_PER_MM = 1000
_UM_PER_INCH = 25400
_UM_PER_THOU = Decimal("25.4")

_PATTERN = re.compile(
    r"^\s*(?P<value>[+-]?\d+(?:\.\d+)?)\s*(?P<unit>um|mm|cm|m|in|\"|thou|mil)\s*$",
    re.IGNORECASE,
)


@dataclass(frozen=True, order=True, slots=True)
class Length:
    """A length to the micrometre. Immutable, orderable, exact."""

    um: int

    ZERO: ClassVar[Length]

    def __post_init__(self) -> None:
        if not isinstance(self.um, int) or isinstance(self.um, bool):
            raise ValidationError("a length is a whole number of micrometres", given=self.um)

    # construction ---------------------------------------------------------

    @classmethod
    def from_mm(cls, mm: float | Decimal | str) -> Length:
        return cls(_round_half_up(Decimal(str(mm)) * _UM_PER_MM))

    @classmethod
    def from_cm(cls, cm: float | Decimal | str) -> Length:
        return cls(_round_half_up(Decimal(str(cm)) * _UM_PER_MM * 10))

    @classmethod
    def from_m(cls, m: float | Decimal | str) -> Length:
        return cls(_round_half_up(Decimal(str(m)) * _UM_PER_MM * 1000))

    @classmethod
    def from_inch(cls, inch: float | Decimal | str) -> Length:
        return cls(_round_half_up(Decimal(str(inch)) * _UM_PER_INCH))

    @classmethod
    def from_thou(cls, thou: float | Decimal | str) -> Length:
        """Thousandths of an inch. The film crew still calls these mils."""
        return cls(_round_half_up(Decimal(str(thou)) * _UM_PER_THOU))

    @classmethod
    def parse(cls, text: str) -> Length:
        """Read a length off a procedure line or a spreadsheet cell."""
        match = _PATTERN.match(text)
        if match is None:
            raise ValidationError("not a length", text=text)
        value = match.group("value")
        unit = match.group("unit").lower()
        if unit == "um":
            return cls(_round_half_up(Decimal(value)))
        if unit == "mm":
            return cls.from_mm(value)
        if unit == "cm":
            return cls.from_cm(value)
        if unit == "m":
            return cls.from_m(value)
        if unit in ("in", '"'):
            return cls.from_inch(value)
        return cls.from_thou(value)

    # reading --------------------------------------------------------------

    @property
    def mm(self) -> Decimal:
        return Decimal(self.um) / _UM_PER_MM

    @property
    def cm(self) -> Decimal:
        return Decimal(self.um) / (_UM_PER_MM * 10)

    @property
    def m(self) -> Decimal:
        return Decimal(self.um) / (_UM_PER_MM * 1000)

    @property
    def inch(self) -> Decimal:
        return Decimal(self.um) / _UM_PER_INCH

    @property
    def thou(self) -> Decimal:
        return Decimal(self.um) / _UM_PER_THOU

    # arithmetic -----------------------------------------------------------

    def __add__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self.um + other.um)

    def __sub__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self.um - other.um)

    def __mul__(self, factor: int | float | Decimal) -> Length:
        return Length(_round_half_up(Decimal(self.um) * Decimal(str(factor))))

    __rmul__ = __mul__

    def __truediv__(self, divisor: int | float | Decimal) -> Length:
        d = Decimal(str(divisor))
        if d == 0:
            raise ValidationError("cannot divide a length by zero")
        return Length(_round_half_up(Decimal(self.um) / d))

    def __neg__(self) -> Length:
        return Length(-self.um)

    def __abs__(self) -> Length:
        return Length(abs(self.um))

    def ratio(self, other: Length) -> Decimal:
        """Dimensionless. Magnification and unsharpness both want this."""
        if other.um == 0:
            raise ValidationError("cannot take a ratio against a zero length")
        return Decimal(self.um) / Decimal(other.um)

    # presentation ---------------------------------------------------------

    def format_mm(self, places: int = 2) -> str:
        return f"{_quantize(self.mm, places)} mm"

    def format_inch(self, places: int = 3) -> str:
        return f"{_quantize(self.inch, places)} in"

    def format_m(self, places: int = 1) -> str:
        return f"{_quantize(self.m, places)} m"

    def __str__(self) -> str:
        return self.format_mm()

    def __repr__(self) -> str:
        return f"Length({self.um})"


Length.ZERO = Length(0)


def _quantize(value: Decimal, places: int) -> Decimal:
    """Half up, because a shop floor does not do banker's rounding."""
    return value.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)


def _round_half_up(value: Decimal) -> int:
    """Away from zero at the half, which is what a shop floor rounds."""
    if value >= 0:
        return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))
    return -int((-value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def mm(value: float | Decimal | str) -> Length:
    return Length.from_mm(value)


def inch(value: float | Decimal | str) -> Length:
    return Length.from_inch(value)


def thou(value: float | Decimal | str) -> Length:
    return Length.from_thou(value)
