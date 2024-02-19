"""Durations, held as whole milliseconds.

An exposure is quoted in minutes and seconds and it is written on the technique
sheet the way the crew will set the timer: one minute forty. A crank out is
quoted in seconds. A stay time inside a boundary is quoted in minutes. All of
them are the same quantity and none of them should be a bare float, because a
float that means seconds and a float that means minutes look identical right up
until somebody multiplies one by a gamma constant.

Milliseconds are the store. Timers on exposure devices resolve to a second and
nothing in the trade quotes finer, so a whole millisecond is more resolution
than the equipment has and the arithmetic stays exact.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import ClassVar

from qu_imager.errors import ValidationError

MS_PER_SECOND = 1000
MS_PER_MINUTE = 60_000
MS_PER_HOUR = 3_600_000

_CLOCK = re.compile(r"^\s*(?:(?P<h>\d+):)?(?P<m>\d{1,2}):(?P<s>\d{1,2}(?:\.\d+)?)\s*$")
_COMPOUND = re.compile(
    r"^\s*(?:(?P<h>\d+(?:\.\d+)?)\s*h)?\s*"
    r"(?:(?P<m>\d+(?:\.\d+)?)\s*m(?!s))?\s*"
    r"(?:(?P<s>\d+(?:\.\d+)?)\s*s)?\s*$",
    re.IGNORECASE,
)


@dataclass(frozen=True, order=True, slots=True)
class Duration:
    """A span of time, to the millisecond."""

    ms: int

    ZERO: ClassVar[Duration]

    def __post_init__(self) -> None:
        if not isinstance(self.ms, int) or isinstance(self.ms, bool):
            raise ValidationError("a duration is a whole number of milliseconds", given=self.ms)
        if self.ms < 0:
            raise ValidationError("a duration cannot be negative", given=self.ms)

    @classmethod
    def from_seconds(cls, seconds: float | Decimal | str) -> Duration:
        return cls(_round_half_up(Decimal(str(seconds)) * MS_PER_SECOND))

    @classmethod
    def from_minutes(cls, minutes: float | Decimal | str) -> Duration:
        return cls(_round_half_up(Decimal(str(minutes)) * MS_PER_MINUTE))

    @classmethod
    def from_hours(cls, hours: float | Decimal | str) -> Duration:
        return cls(_round_half_up(Decimal(str(hours)) * MS_PER_HOUR))

    @classmethod
    def parse(cls, text: str) -> Duration:
        """Read a duration written any of the three ways the office writes it."""
        clock = _CLOCK.match(text)
        if clock is not None:
            hours = Decimal(clock.group("h") or 0)
            minutes = Decimal(clock.group("m"))
            seconds = Decimal(clock.group("s"))
            if minutes > 59 or seconds >= 60:
                raise ValidationError("clock time out of range", text=text)
            total = hours * MS_PER_HOUR + minutes * MS_PER_MINUTE + seconds * MS_PER_SECOND
            return cls(_round_half_up(total))
        compound = _COMPOUND.match(text)
        if compound is None or not any(compound.group(g) for g in ("h", "m", "s")):
            raise ValidationError("not a duration", text=text)
        total = (
            Decimal(compound.group("h") or 0) * MS_PER_HOUR
            + Decimal(compound.group("m") or 0) * MS_PER_MINUTE
            + Decimal(compound.group("s") or 0) * MS_PER_SECOND
        )
        return cls(_round_half_up(total))

    @property
    def seconds(self) -> Decimal:
        return Decimal(self.ms) / MS_PER_SECOND

    @property
    def minutes(self) -> Decimal:
        return Decimal(self.ms) / MS_PER_MINUTE

    @property
    def hours(self) -> Decimal:
        return Decimal(self.ms) / MS_PER_HOUR

    def __add__(self, other: Duration) -> Duration:
        if not isinstance(other, Duration):
            return NotImplemented
        return Duration(self.ms + other.ms)

    def __sub__(self, other: Duration) -> Duration:
        if not isinstance(other, Duration):
            return NotImplemented
        if other.ms > self.ms:
            raise ValidationError("a duration cannot go below zero", left=self.ms, right=other.ms)
        return Duration(self.ms - other.ms)

    def __mul__(self, factor: int | float | Decimal) -> Duration:
        f = Decimal(str(factor))
        if f < 0:
            raise ValidationError("cannot scale a duration by a negative factor", factor=factor)
        return Duration(_round_half_up(Decimal(self.ms) * f))

    __rmul__ = __mul__

    def __truediv__(self, divisor: int | float | Decimal) -> Duration:
        d = Decimal(str(divisor))
        if d <= 0:
            raise ValidationError("cannot divide a duration by that", divisor=divisor)
        return Duration(_round_half_up(Decimal(self.ms) / d))

    def ratio(self, other: Duration) -> Decimal:
        if other.ms == 0:
            raise ValidationError("cannot take a ratio against a zero duration")
        return Decimal(self.ms) / Decimal(other.ms)

    def round_up_to_second(self) -> Duration:
        """A timer that only takes whole seconds must not be set short."""
        whole, remainder = divmod(self.ms, MS_PER_SECOND)
        return Duration((whole + 1) * MS_PER_SECOND) if remainder else self

    def as_timer(self) -> str:
        """What gets written in the time box on the technique sheet."""
        total = self.round_up_to_second().ms // MS_PER_SECOND
        minutes, seconds = divmod(total, 60)
        hours, minutes = divmod(minutes, 60)
        if hours:
            return f"{hours}:{minutes:02d}:{seconds:02d}"
        return f"{minutes}:{seconds:02d}"

    def __str__(self) -> str:
        return self.as_timer()

    def __repr__(self) -> str:
        return f"Duration({self.ms})"


Duration.ZERO = Duration(0)


def _round_half_up(value: Decimal) -> int:
    return int(value.quantize(Decimal(1), rounding=ROUND_HALF_UP))


def seconds(value: float | Decimal | str) -> Duration:
    return Duration.from_seconds(value)


def minutes(value: float | Decimal | str) -> Duration:
    return Duration.from_minutes(value)
