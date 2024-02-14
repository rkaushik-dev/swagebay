"""Source activity, held as whole becquerels.

Nobody in the field says becquerel. A projector holds ninety curies of iridium
and the crew talks in curies, but the licence, the ship papers and the leak
test certificate are all in becquerels because the regulator writes them that
way. Both numbers have to come out of the same store without drifting.

One curie is 37 000 000 000 becquerels by definition, an exact integer, so
whole becquerels hold every curie value the trade quotes and the conversion is
reversible. A hundredth of a curie is 370 MBq on the nose.

Activity is not a length and it is not a dose. The type refuses to mix with
either, which has already caught one place where a gamma constant was applied
to the wrong operand.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import ClassVar

from qu_imager.errors import ValidationError

BQ_PER_CURIE = 37_000_000_000

_PATTERN = re.compile(
    r"^\s*(?P<value>[+-]?\d+(?:\.\d+)?)\s*(?P<unit>bq|kbq|mbq|gbq|tbq|ci|mci|uci)\s*$",
    re.IGNORECASE,
)

_MULTIPLIERS: dict[str, Decimal] = {
    "bq": Decimal(1),
    "kbq": Decimal(1_000),
    "mbq": Decimal(1_000_000),
    "gbq": Decimal(1_000_000_000),
    "tbq": Decimal(1_000_000_000_000),
    "ci": Decimal(BQ_PER_CURIE),
    "mci": Decimal(BQ_PER_CURIE) / 1000,
    "uci": Decimal(BQ_PER_CURIE) / 1_000_000,
}


@dataclass(frozen=True, order=True, slots=True)
class Activity:
    """An amount of radioactivity, to the becquerel."""

    bq: int

    ZERO: ClassVar[Activity]

    def __post_init__(self) -> None:
        if not isinstance(self.bq, int) or isinstance(self.bq, bool):
            raise ValidationError("an activity is a whole number of becquerels", given=self.bq)
        if self.bq < 0:
            raise ValidationError("an activity cannot be negative", given=self.bq)

    @classmethod
    def from_curie(cls, curie: float | Decimal | str) -> Activity:
        return cls(_round_half_up(Decimal(str(curie)) * BQ_PER_CURIE))

    @classmethod
    def from_millicurie(cls, mci: float | Decimal | str) -> Activity:
        return cls(_round_half_up(Decimal(str(mci)) * BQ_PER_CURIE / 1000))

    @classmethod
    def from_gbq(cls, gbq: float | Decimal | str) -> Activity:
        return cls(_round_half_up(Decimal(str(gbq)) * 1_000_000_000))

    @classmethod
    def from_tbq(cls, tbq: float | Decimal | str) -> Activity:
        return cls(_round_half_up(Decimal(str(tbq)) * 1_000_000_000_000))

    @classmethod
    def parse(cls, text: str) -> Activity:
        match = _PATTERN.match(text)
        if match is None:
            raise ValidationError("not an activity", text=text)
        unit = match.group("unit").lower()
        return cls(_round_half_up(Decimal(match.group("value")) * _MULTIPLIERS[unit]))

    @property
    def curie(self) -> Decimal:
        return Decimal(self.bq) / BQ_PER_CURIE

    @property
    def gbq(self) -> Decimal:
        return Decimal(self.bq) / 1_000_000_000

    @property
    def tbq(self) -> Decimal:
        return Decimal(self.bq) / 1_000_000_000_000

    def __add__(self, other: Activity) -> Activity:
        if not isinstance(other, Activity):
            return NotImplemented
        return Activity(self.bq + other.bq)

    def __sub__(self, other: Activity) -> Activity:
        if not isinstance(other, Activity):
            return NotImplemented
        if other.bq > self.bq:
            raise ValidationError("an activity cannot go below zero", left=self.bq, right=other.bq)
        return Activity(self.bq - other.bq)

    def __mul__(self, factor: int | float | Decimal) -> Activity:
        f = Decimal(str(factor))
        if f < 0:
            raise ValidationError("cannot scale an activity by a negative factor", factor=factor)
        return Activity(_round_half_up(Decimal(self.bq) * f))

    __rmul__ = __mul__

    def ratio(self, other: Activity) -> Decimal:
        if other.bq == 0:
            raise ValidationError("cannot take a ratio against a spent source")
        return Decimal(self.bq) / Decimal(other.bq)

    def is_spent(self, floor: Activity) -> bool:
        """Below the floor a source is no longer worth taking to a job."""
        return self.bq < floor.bq

    def format_curie(self, places: int = 1) -> str:
        return f"{_quantize(self.curie, places)} Ci"

    def format_tbq(self, places: int = 2) -> str:
        return f"{_quantize(self.tbq, places)} TBq"

    def __str__(self) -> str:
        return self.format_curie()

    def __repr__(self) -> str:
        return f"Activity({self.bq})"


Activity.ZERO = Activity(0)


def _quantize(value: Decimal, places: int) -> Decimal:
    return value.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)


def _round_half_up(value: Decimal) -> int:
    return int((value + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def curie(value: float | Decimal | str) -> Activity:
    return Activity.from_curie(value)


def gbq(value: float | Decimal | str) -> Activity:
    return Activity.from_gbq(value)
