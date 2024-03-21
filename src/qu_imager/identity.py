"""The identifiers everything else is filed under.

A radiograph that cannot be tied back to a weld is worthless, and the tie is
made with strings that people write on lead tape at four in the morning with
cold hands. So the strings have a grammar, it is checked on the way in, and the
canonical form is what gets stored.

A weld identifier carries its repair history in the identifier itself. GW-104
is the original. GW-104R1 is the first repair of it, GW-104R2 the second. This
is not a decoration: the repair round is what the repair rate is computed
against, and a shop that files repairs as fresh welds shows a repair rate of
zero forever.

Clock positions are the other thing worth being strict about. A crew writes
1200, 12:00, 12, or top for the same place on the pipe, and a report that
carries all four is a report nobody can sort.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError

_JOB = re.compile(r"^(?P<year>\d{2})-(?P<serial>\d{4})$")
_WELD = re.compile(r"^(?P<prefix>[A-Z]{2})-(?P<number>\d{1,5})(?:R(?P<repair>\d{1,2}))?$")
_CLOCK = re.compile(r"^(?P<hour>\d{1,2})(?::?(?P<minute>[0-5]\d))?$")

WELD_PREFIXES = {
    "GW": "girth weld",
    "LW": "longitudinal weld",
    "FW": "fillet weld",
    "BW": "branch weld",
    "SW": "socket weld",
}


@dataclass(frozen=True, order=True, slots=True)
class JobNumber:
    """Two digit year and a four digit serial, the way the office files it."""

    year: int
    serial: int

    def __post_init__(self) -> None:
        if not 0 <= self.year <= 99:
            raise ValidationError("a job year is two digits", year=self.year)
        if not 1 <= self.serial <= 9999:
            raise ValidationError("a job serial is one to four digits", serial=self.serial)

    @classmethod
    def parse(cls, text: str) -> JobNumber:
        match = _JOB.match(text.strip().upper())
        if match is None:
            raise ValidationError("not a job number", text=text)
        return cls(year=int(match.group("year")), serial=int(match.group("serial")))

    def next(self) -> JobNumber:
        if self.serial == 9999:
            raise ValidationError("the job serial is exhausted for this year", year=self.year)
        return JobNumber(self.year, self.serial + 1)

    def __str__(self) -> str:
        return f"{self.year:02d}-{self.serial:04d}"


@dataclass(frozen=True, order=True, slots=True)
class WeldId:
    """A weld, and which repair round of it this is."""

    prefix: str
    number: int
    repair: int = 0

    def __post_init__(self) -> None:
        if self.prefix not in WELD_PREFIXES:
            raise ValidationError("unknown weld prefix", prefix=self.prefix)
        if not 1 <= self.number <= 99999:
            raise ValidationError("a weld number is one to five digits", number=self.number)
        if not 0 <= self.repair <= 99:
            raise ValidationError("a repair round is zero to ninety nine", repair=self.repair)

    @classmethod
    def parse(cls, text: str) -> WeldId:
        match = _WELD.match(text.strip().upper())
        if match is None:
            raise ValidationError("not a weld identifier", text=text)
        repair = match.group("repair")
        return cls(
            prefix=match.group("prefix"),
            number=int(match.group("number")),
            repair=int(repair) if repair else 0,
        )

    @property
    def is_repair(self) -> bool:
        return self.repair > 0

    @property
    def original(self) -> WeldId:
        return WeldId(self.prefix, self.number, 0)

    def next_repair(self) -> WeldId:
        return WeldId(self.prefix, self.number, self.repair + 1)

    def describe(self) -> str:
        kind = WELD_PREFIXES[self.prefix]
        if self.repair:
            return f"{kind} {self.number}, repair {self.repair}"
        return f"{kind} {self.number}"

    def __str__(self) -> str:
        suffix = f"R{self.repair}" if self.repair else ""
        return f"{self.prefix}-{self.number}{suffix}"


@dataclass(frozen=True, order=True, slots=True)
class ClockPosition:
    """A position round a pipe, stored as minutes past twelve."""

    minutes: int

    def __post_init__(self) -> None:
        if not 0 <= self.minutes < 720:
            raise ValidationError("a clock position is inside twelve hours", minutes=self.minutes)

    @classmethod
    def parse(cls, text: str) -> ClockPosition:
        cleaned = text.strip().lower()
        if cleaned in _NAMED:
            return cls(_NAMED[cleaned])
        match = _CLOCK.match(cleaned)
        if match is None:
            raise ValidationError("not a clock position", text=text)
        hour = int(match.group("hour"))
        minute = int(match.group("minute") or 0)
        if not 1 <= hour <= 12:
            raise ValidationError("a clock hour is one to twelve", hour=hour)
        return cls(((hour % 12) * 60 + minute) % 720)

    @classmethod
    def from_degrees(cls, degrees: Decimal) -> ClockPosition:
        wrapped = degrees % Decimal(360)
        return cls(int(wrapped * 2))

    @property
    def degrees(self) -> Decimal:
        return Decimal(self.minutes) / 2

    def arc_to(self, other: ClockPosition) -> int:
        """Minutes clockwise from here to there."""
        return (other.minutes - self.minutes) % 720

    def __str__(self) -> str:
        hour = self.minutes // 60
        minute = self.minutes % 60
        return f"{12 if hour == 0 else hour}:{minute:02d}"


_NAMED: dict[str, int] = {"top": 0, "bottom": 360, "12": 0, "6": 360}
