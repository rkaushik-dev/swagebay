"""Dose and dose rate, held as whole nanosieverts.

The regulator writes microsieverts. The survey meters in the trucks read
milliroentgen per hour, because they were bought in the nineties and nobody
replaces a working meter. Both appear on the same survey sheet and the office
has to reconcile them.

The conversion this package uses is the field convention: one milliroentgen is
ten microsieverts. It is not a physical identity. Exposure in air and dose
equivalent in tissue are different quantities and the ratio depends on photon
energy, but every procedure in the file, every posting rule and every meter
scale card in the trucks is written on the ten to one convention, so that is
what the software reproduces. If it ever needs to be energy dependent it goes
behind a lookup and this constant becomes the default entry.

Nanosieverts are the store. A boundary at 20 uSv/h and a meter reading of
0.02 mR/h both land on whole nanosieverts with room to spare.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import ClassVar

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import MS_PER_HOUR, Duration

NSV_PER_USV = 1_000
NSV_PER_MSV = 1_000_000
USV_PER_MR = Decimal(10)

_DOSE = re.compile(
    r"^\s*(?P<value>\d+(?:\.\d+)?)\s*(?P<unit>nsv|usv|msv|sv|mrem|rem)\s*$", re.IGNORECASE
)
_RATE = re.compile(
    r"^\s*(?P<value>\d+(?:\.\d+)?)\s*(?P<unit>nsv|usv|msv|mr|r)\s*/\s*h(?:r)?\s*$",
    re.IGNORECASE,
)

_DOSE_MULTIPLIERS: dict[str, Decimal] = {
    "nsv": Decimal(1),
    "usv": Decimal(NSV_PER_USV),
    "msv": Decimal(NSV_PER_MSV),
    "sv": Decimal(NSV_PER_MSV) * 1000,
    "mrem": Decimal(NSV_PER_USV) * 10,
    "rem": Decimal(NSV_PER_MSV) * 10,
}

_RATE_MULTIPLIERS: dict[str, Decimal] = {
    "nsv": Decimal(1),
    "usv": Decimal(NSV_PER_USV),
    "msv": Decimal(NSV_PER_MSV),
    "mr": Decimal(NSV_PER_USV) * USV_PER_MR,
    "r": Decimal(NSV_PER_USV) * USV_PER_MR * 1000,
}


@dataclass(frozen=True, order=True, slots=True)
class Dose:
    """An accumulated dose equivalent, to the nanosievert."""

    nsv: int

    ZERO: ClassVar[Dose]

    def __post_init__(self) -> None:
        if not isinstance(self.nsv, int) or isinstance(self.nsv, bool):
            raise ValidationError("a dose is a whole number of nanosieverts", given=self.nsv)
        if self.nsv < 0:
            raise ValidationError("a dose cannot be negative", given=self.nsv)

    @classmethod
    def from_usv(cls, usv: float | Decimal | str) -> Dose:
        return cls(_round_half_up(Decimal(str(usv)) * NSV_PER_USV))

    @classmethod
    def from_msv(cls, msv: float | Decimal | str) -> Dose:
        return cls(_round_half_up(Decimal(str(msv)) * NSV_PER_MSV))

    @classmethod
    def from_mrem(cls, mrem: float | Decimal | str) -> Dose:
        return cls(_round_half_up(Decimal(str(mrem)) * NSV_PER_USV * 10))

    @classmethod
    def parse(cls, text: str) -> Dose:
        match = _DOSE.match(text)
        if match is None:
            raise ValidationError("not a dose", text=text)
        unit = match.group("unit").lower()
        return cls(_round_half_up(Decimal(match.group("value")) * _DOSE_MULTIPLIERS[unit]))

    @property
    def usv(self) -> Decimal:
        return Decimal(self.nsv) / NSV_PER_USV

    @property
    def msv(self) -> Decimal:
        return Decimal(self.nsv) / NSV_PER_MSV

    @property
    def mrem(self) -> Decimal:
        return Decimal(self.nsv) / (NSV_PER_USV * 10)

    def __add__(self, other: Dose) -> Dose:
        if not isinstance(other, Dose):
            return NotImplemented
        return Dose(self.nsv + other.nsv)

    def __sub__(self, other: Dose) -> Dose:
        if not isinstance(other, Dose):
            return NotImplemented
        if other.nsv > self.nsv:
            raise ValidationError("a dose cannot go below zero", left=self.nsv, right=other.nsv)
        return Dose(self.nsv - other.nsv)

    def __mul__(self, factor: int | float | Decimal) -> Dose:
        f = Decimal(str(factor))
        if f < 0:
            raise ValidationError("cannot scale a dose by a negative factor", factor=factor)
        return Dose(_round_half_up(Decimal(self.nsv) * f))

    __rmul__ = __mul__

    def fraction_of(self, limit: Dose) -> Decimal:
        if limit.nsv == 0:
            raise ValidationError("cannot take a fraction of a zero limit")
        return Decimal(self.nsv) / Decimal(limit.nsv)

    def format_usv(self, places: int = 1) -> str:
        return f"{_quantize(self.usv, places)} uSv"

    def format_mrem(self, places: int = 1) -> str:
        return f"{_quantize(self.mrem, places)} mrem"

    def format_msv(self, places: int = 2) -> str:
        return f"{_quantize(self.msv, places)} mSv"

    def __str__(self) -> str:
        return self.format_usv()

    def __repr__(self) -> str:
        return f"Dose({self.nsv})"


@dataclass(frozen=True, order=True, slots=True)
class DoseRate:
    """A dose equivalent rate, to the nanosievert in an hour."""

    nsv_per_hour: int

    ZERO: ClassVar[DoseRate]

    def __post_init__(self) -> None:
        if not isinstance(self.nsv_per_hour, int) or isinstance(self.nsv_per_hour, bool):
            raise ValidationError(
                "a dose rate is a whole number of nanosieverts an hour", given=self.nsv_per_hour
            )
        if self.nsv_per_hour < 0:
            raise ValidationError("a dose rate cannot be negative", given=self.nsv_per_hour)

    @classmethod
    def from_usv_per_hour(cls, usv: float | Decimal | str) -> DoseRate:
        return cls(_round_half_up(Decimal(str(usv)) * NSV_PER_USV))

    @classmethod
    def from_msv_per_hour(cls, msv: float | Decimal | str) -> DoseRate:
        return cls(_round_half_up(Decimal(str(msv)) * NSV_PER_MSV))

    @classmethod
    def from_mr_per_hour(cls, mr: float | Decimal | str) -> DoseRate:
        """What the meter in the truck actually reads."""
        return cls(_round_half_up(Decimal(str(mr)) * USV_PER_MR * NSV_PER_USV))

    @classmethod
    def parse(cls, text: str) -> DoseRate:
        match = _RATE.match(text)
        if match is None:
            raise ValidationError("not a dose rate", text=text)
        unit = match.group("unit").lower()
        return cls(_round_half_up(Decimal(match.group("value")) * _RATE_MULTIPLIERS[unit]))

    @property
    def usv_per_hour(self) -> Decimal:
        return Decimal(self.nsv_per_hour) / NSV_PER_USV

    @property
    def msv_per_hour(self) -> Decimal:
        return Decimal(self.nsv_per_hour) / NSV_PER_MSV

    @property
    def mr_per_hour(self) -> Decimal:
        return Decimal(self.nsv_per_hour) / (NSV_PER_USV * USV_PER_MR)

    def __add__(self, other: DoseRate) -> DoseRate:
        if not isinstance(other, DoseRate):
            return NotImplemented
        return DoseRate(self.nsv_per_hour + other.nsv_per_hour)

    def __mul__(self, factor: int | float | Decimal) -> DoseRate:
        f = Decimal(str(factor))
        if f < 0:
            raise ValidationError("cannot scale a dose rate by a negative factor", factor=factor)
        return DoseRate(_round_half_up(Decimal(self.nsv_per_hour) * f))

    __rmul__ = __mul__

    def over(self, span: Duration) -> Dose:
        """The dose somebody standing here for that long would take."""
        hours = Decimal(span.ms) / MS_PER_HOUR
        return Dose(_round_half_up(Decimal(self.nsv_per_hour) * hours))

    def stay_time(self, budget: Dose) -> Duration:
        """How long the budget lasts at this rate. Truncated, never rounded up."""
        if self.nsv_per_hour == 0:
            raise ValidationError("stay time is unbounded at a zero dose rate")
        hours = Decimal(budget.nsv) / Decimal(self.nsv_per_hour)
        return Duration(int(hours * MS_PER_HOUR))

    def format_usv_per_hour(self, places: int = 1) -> str:
        return f"{_quantize(self.usv_per_hour, places)} uSv/h"

    def format_mr_per_hour(self, places: int = 2) -> str:
        return f"{_quantize(self.mr_per_hour, places)} mR/h"

    def __str__(self) -> str:
        return self.format_usv_per_hour()

    def __repr__(self) -> str:
        return f"DoseRate({self.nsv_per_hour})"


Dose.ZERO = Dose(0)
DoseRate.ZERO = DoseRate(0)


def _quantize(value: Decimal, places: int) -> Decimal:
    return value.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)


def _round_half_up(value: Decimal) -> int:
    return int(value.quantize(Decimal(1), rounding=ROUND_HALF_UP))


def usv(value: float | Decimal | str) -> Dose:
    return Dose.from_usv(value)


def usv_per_hour(value: float | Decimal | str) -> DoseRate:
    return DoseRate.from_usv_per_hour(value)


def mr_per_hour(value: float | Decimal | str) -> DoseRate:
    return DoseRate.from_mr_per_hour(value)
