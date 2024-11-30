"""Processor control, which is the quietest way a shop goes wrong.

Nothing about a processor fails loudly. The developer gets tired, the
temperature drifts a degree, and every film comes out a little thinner than the
one before. No single film is obviously bad. Six weeks later the shop is
shooting twenty percent longer than the technique says and nobody knows why.

The control is a strip: a pre exposed step wedge put through the processor
every morning and read at three points. Speed is the density at the middle
step, contrast is the difference between two steps either side of it, and fog
is the density of an unexposed corner. Each one is compared to the value on the
day the processor was set up, not to yesterday, because comparing to yesterday
follows a drift down instead of catching it.

The limits are the ones in the written practice and they are tighter than they
look. Speed may move by fifteen hundredths of a density unit, contrast by
twenty, fog by five. A strip outside any of them stops the processor until
somebody has looked at it, which is expensive and is the point.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from qu_imager.errors import ValidationError

SPEED_TOLERANCE = Decimal("0.15")
CONTRAST_TOLERANCE = Decimal("0.20")
FOG_TOLERANCE = Decimal("0.05")


@dataclass(frozen=True, slots=True)
class Strip:
    """One control strip, read at three points."""

    read_on: date
    speed: Decimal
    contrast: Decimal
    fog: Decimal
    operator: str

    def __post_init__(self) -> None:
        if not self.operator.strip():
            raise ValidationError("a control strip is signed")
        for name, value in (
            ("speed", self.speed),
            ("contrast", self.contrast),
            ("fog", self.fog),
        ):
            if value <= 0:
                raise ValidationError("a strip reading has to be positive", reading=name)


@dataclass(frozen=True, slots=True)
class Baseline:
    """What the processor read the day it was set up."""

    established_on: date
    speed: Decimal
    contrast: Decimal
    fog: Decimal


@dataclass(frozen=True, slots=True)
class Excursion:
    """One reading outside its band."""

    reading: str
    measured: Decimal
    baseline: Decimal
    tolerance: Decimal

    @property
    def drift(self) -> Decimal:
        return self.measured - self.baseline

    @property
    def direction(self) -> str:
        return "high" if self.drift > 0 else "low"

    def describe(self) -> str:
        return (
            f"{self.reading} is {abs(self.drift)} {self.direction} of the baseline, "
            f"over the {self.tolerance} allowed"
        )


def excursions(strip: Strip, baseline: Baseline) -> tuple[Excursion, ...]:
    """Every reading on one strip that is outside its band."""
    checks = (
        ("speed", strip.speed, baseline.speed, SPEED_TOLERANCE),
        ("contrast", strip.contrast, baseline.contrast, CONTRAST_TOLERANCE),
        ("fog", strip.fog, baseline.fog, FOG_TOLERANCE),
    )
    return tuple(
        Excursion(name, measured, base, tolerance)
        for name, measured, base, tolerance in checks
        if abs(measured - base) > tolerance
    )


def in_control(strip: Strip, baseline: Baseline) -> bool:
    return not excursions(strip, baseline)


def trend(strips: tuple[Strip, ...], baseline: Baseline) -> tuple[Decimal, ...]:
    """The speed drift over a run of strips, oldest first."""
    ordered = sorted(strips, key=lambda s: s.read_on)
    return tuple(s.speed - baseline.speed for s in ordered)


def drifting(strips: tuple[Strip, ...], baseline: Baseline, run: int = 5) -> bool:
    """Whether the last few strips have all moved the same way.

    A run all on one side of the baseline is a drift even when every strip is
    inside its band, and it is the thing the daily check exists to catch. One
    strip a hundredth low is noise. Five in a row a hundredth low is a
    processor on its way out.
    """
    if run < 2:
        raise ValidationError("a run is at least two strips", run=run)
    drifts = trend(strips, baseline)
    if len(drifts) < run:
        return False
    recent = drifts[-run:]
    return all(d > 0 for d in recent) or all(d < 0 for d in recent)


def days_since_baseline(strip: Strip, baseline: Baseline) -> int:
    return (strip.read_on - baseline.established_on).days
