"""Repair rate over time, which is the number a client watches and a shop dreads.

A repair rate is repaired welds over welds shot and it is only meaningful with
its denominator attached. Four percent on a thousand welds is a shop running
well. Four percent on twenty five welds is one repair, which is noise. So every
figure here carries the count it was computed from, and anything that hides the
count is a figure somebody will misread.

The band is the other half. A shop knows what its own rate normally is, and the
useful question is not whether this month is above zero but whether it is
outside the band the shop normally sits in. That band is set from the shop's
own history rather than from a target, because a target is a management number
and the history is a measurement.

Per welder is where this gets political and the code stays out of it. It
reports the rate and the count. It does not rank, it does not flag, and it does
not compute a rate on a denominator too small to mean anything, because a
welder with three welds and one repair is not a thirty three percent welder.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

from qu_imager.errors import ValidationError

MINIMUM_DENOMINATOR = 20
"""Below this a rate is not reported, because it is one weld either way."""


@dataclass(frozen=True, slots=True)
class Rate:
    """A rate with the count it came from, which is the only honest shape."""

    repaired: int
    shot: int

    def __post_init__(self) -> None:
        if self.shot < 0 or self.repaired < 0:
            raise ValidationError("counts are not negative")
        if self.repaired > self.shot:
            raise ValidationError(
                "more welds repaired than shot", repaired=self.repaired, shot=self.shot
            )

    @property
    def is_reportable(self) -> bool:
        return self.shot >= MINIMUM_DENOMINATOR

    @property
    def value(self) -> Decimal:
        if self.shot == 0:
            return Decimal(0)
        return Decimal(self.repaired) / Decimal(self.shot)

    def describe(self) -> str:
        if not self.is_reportable:
            return f"{self.repaired} of {self.shot}, too few to rate"
        return f"{self.value:.2%} on {self.shot}"


@dataclass(frozen=True, slots=True)
class Period:
    """One month of work."""

    year: int
    month: int
    rate: Rate

    def __post_init__(self) -> None:
        if not 1 <= self.month <= 12:
            raise ValidationError("a month is one to twelve", month=self.month)

    @property
    def starts_on(self) -> date:
        return date(self.year, self.month, 1)

    def label(self) -> str:
        return f"{self.year:04d}-{self.month:02d}"


@dataclass(frozen=True, slots=True)
class Band:
    """The range the shop normally sits in, from its own history."""

    centre: Decimal
    low: Decimal
    high: Decimal

    def contains(self, rate: Rate) -> bool:
        return self.low <= rate.value <= self.high

    def describe(self) -> str:
        return f"{self.centre:.2%} band {self.low:.2%} to {self.high:.2%}"


def combine(rates: Iterable[Rate]) -> Rate:
    """Pool several periods, which is not the average of their rates."""
    repaired = 0
    shot = 0
    for rate in rates:
        repaired += rate.repaired
        shot += rate.shot
    return Rate(repaired=repaired, shot=shot)


def band_from(periods: Iterable[Period], spread: Decimal = Decimal("1.5")) -> Band:
    """Set the band from the shop's own history rather than from a target.

    The spread is a multiple either side of the pooled rate rather than a
    standard deviation. A repair rate is a proportion on a small denominator
    and its distribution is nothing like normal, so a standard deviation from a
    handful of months is a number with a decimal point and no meaning. A
    multiple is crude, it is understood by everybody who reads it, and it does
    not pretend.
    """
    if spread <= 1:
        raise ValidationError("a spread is greater than one", spread=str(spread))
    reportable = [p for p in periods if p.rate.is_reportable]
    if not reportable:
        raise ValidationError("there is not enough history to set a band")
    pooled = combine(p.rate for p in reportable).value
    return Band(centre=pooled, low=pooled / spread, high=pooled * spread)


def excursions(periods: Iterable[Period], band: Band) -> tuple[Period, ...]:
    """Months outside the band, ignoring the ones with too few welds to rate."""
    return tuple(p for p in periods if p.rate.is_reportable and not band.contains(p.rate))


def rising(periods: Iterable[Period], run: int = 3) -> bool:
    """Whether the last few reportable months each beat the one before."""
    if run < 2:
        raise ValidationError("a run is at least two months", run=run)
    ordered = [p for p in sorted(periods, key=lambda p: p.starts_on) if p.rate.is_reportable]
    if len(ordered) < run:
        return False
    recent = ordered[-run:]
    return all(
        later.rate.value > earlier.rate.value
        for earlier, later in zip(recent, recent[1:], strict=False)
    )


def by_welder(counts: dict[str, tuple[int, int]]) -> dict[str, Rate]:
    """Per welder, with the ones on too few welds still carrying their counts."""
    return {
        welder: Rate(repaired=repaired, shot=shot)
        for welder, (repaired, shot) in sorted(counts.items())
    }


def reportable_welders(counts: dict[str, tuple[int, int]]) -> tuple[str, ...]:
    return tuple(w for w, rate in by_welder(counts).items() if rate.is_reportable)


def as_percentage(rate: Rate, places: int = 2) -> Decimal:
    return (rate.value * 100).quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
