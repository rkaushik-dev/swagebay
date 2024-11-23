"""Exposure charts for an x-ray set, which are read differently from isotope charts.

An isotope chart is one curve. A tube chart is a family of them, one per
kilovoltage, and the shooter picks the curve before reading it. That extra
dimension is the whole difference and it is why these did not fold into the
isotope chart type.

The value read off is milliampere seconds rather than curie minutes, and it
does not need decaying because a tube does not get weaker. What it does need is
splitting into a current and a time, and the split is bounded at both ends: the
set will not give more than its rated current, and a time under a second is not
reproducible on a mechanical timer.

Interpolation is logarithmic along a curve and it is refused between curves. A
technique at 165 kilovolts is a real thing on a set with a continuous control,
and reading it by averaging the 150 and 180 curves is not, because the curves
are not evenly spaced and the family is not linear in kilovoltage.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length
from qu_imager.sources.xray import Tube, time_for


@dataclass(frozen=True, slots=True)
class Curve:
    """One kilovoltage, and the thickness to exposure points along it."""

    kv: int
    points: tuple[tuple[Length, Decimal], ...]

    def __post_init__(self) -> None:
        if len(self.points) < 2:
            raise ConfigurationError("a curve needs at least two points", kv=self.kv)
        thicknesses = [t for t, _mas in self.points]
        if thicknesses != sorted(thicknesses):
            raise ConfigurationError("curve points are out of order", kv=self.kv)
        exposures = [m for _t, m in self.points]
        if exposures != sorted(exposures):
            raise ConfigurationError("a curve has to rise", kv=self.kv)

    @property
    def thinnest(self) -> Length:
        return self.points[0][0]

    @property
    def thickest(self) -> Length:
        return self.points[-1][0]

    def covers(self, thickness: Length) -> bool:
        return self.thinnest <= thickness <= self.thickest

    def mas_for(self, thickness: Length) -> Decimal:
        if not self.covers(thickness):
            raise TechniqueError(
                "that thickness is off the end of the curve",
                kv=self.kv,
                thickness_mm=str(thickness.mm),
                from_mm=str(self.thinnest.mm),
                to_mm=str(self.thickest.mm),
            )
        lower = self.points[0]
        for point in self.points:
            if point[0] == thickness:
                return point[1]
            if point[0] > thickness:
                return _interpolate(lower, point, thickness)
            lower = point
        return lower[1]


@dataclass(frozen=True, slots=True)
class TubeChart:
    """A family of curves, valid for one material, film, density and distance."""

    key: str
    material_key: str
    film_key: str
    density: Decimal
    reference_sfd: Length
    curves: tuple[Curve, ...]

    def __post_init__(self) -> None:
        if not self.curves:
            raise ConfigurationError("a chart has at least one curve", key=self.key)
        voltages = [c.kv for c in self.curves]
        if len(set(voltages)) != len(voltages):
            raise ConfigurationError("a chart has two curves at one kilovoltage", key=self.key)

    def kilovoltages(self) -> tuple[int, ...]:
        return tuple(sorted(c.kv for c in self.curves))

    def curve(self, kv: int) -> Curve:
        for candidate in self.curves:
            if candidate.kv == kv:
                return candidate
        raise TechniqueError(
            "no curve is established at that kilovoltage",
            key=self.key,
            kv=kv,
            established=", ".join(str(v) for v in self.kilovoltages()),
        )

    def nearest_kilovoltage(self, kv: int) -> int:
        """The established curve closest to a wanted kilovoltage, harder on a tie."""
        return min(
            self.kilovoltages(), key=lambda established: (abs(established - kv), -established)
        )

    def mas_for(self, kv: int, thickness: Length) -> Decimal:
        return self.curve(kv).mas_for(thickness)


def split(
    mas: Decimal, tube: Tube, preferred_ma: Decimal | None = None
) -> tuple[Decimal, Duration]:
    """Turn an exposure into a current and a time the set can actually hold."""
    if mas <= 0:
        raise ValidationError("an exposure has to be positive")
    current = preferred_ma if preferred_ma is not None else tube.max_ma
    tube.check_current(current)
    raw_seconds = mas / current
    if raw_seconds < 1:
        raise TechniqueError(
            "that exposure is under a second, drop the current or the kilovoltage",
            mas=str(mas),
            ma=str(current),
            seconds=str(raw_seconds),
        )
    return (current, time_for(current, mas))


def _interpolate(
    low: tuple[Length, Decimal], high: tuple[Length, Decimal], thickness: Length
) -> Decimal:
    with localcontext() as ctx:
        ctx.prec = 28
        span = Decimal(high[0].um - low[0].um)
        offset = Decimal(thickness.um - low[0].um)
        low_log = low[1].ln()
        high_log = high[1].ln()
        return (low_log + (high_log - low_log) * offset / span).exp()


def curve_from_mm(kv: int, readings: tuple[tuple[str, str], ...]) -> Curve:
    return Curve(kv=kv, points=tuple((Length.from_mm(t), Decimal(m)) for t, m in readings))
