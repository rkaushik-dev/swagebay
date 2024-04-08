"""Exposure charts, and reading between the lines on one.

An exposure chart is a curve on semi log paper taped inside the door of the
truck. Thickness runs along the bottom, exposure in curie minutes runs up the
side on a log scale, and the curve is close enough to a straight line on that
paper that a shooter reads it with a finger.

Two consequences follow from the log scale and both are in the code.
Interpolating between two chart points is linear in the logarithm, not in the
value, and a chart read with plain linear interpolation comes out short in the
middle of every interval. And a chart cannot be extrapolated. The curve bends
at both ends for reasons that have nothing to do with the straight bit, so
asking for a thickness off the end of the chart gets a refusal, not a number.

A chart is only valid for the four things it was drawn for: an isotope, a
material, a film system, and a density. Everything else is a correction applied
afterwards, and mixing a correction into the chart is how a shop ends up with
nine charts that disagree.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class ChartPoint:
    """One reading off the curve."""

    thickness: Length
    curie_minutes: Decimal

    def __post_init__(self) -> None:
        if self.thickness.um <= 0:
            raise ValidationError("a chart point needs a positive thickness")
        if self.curie_minutes <= 0:
            raise ValidationError("a chart point needs a positive exposure")


@dataclass(frozen=True, slots=True)
class ExposureChart:
    """One curve, valid for one isotope, material, film and density."""

    key: str
    isotope_key: str
    material_key: str
    film_key: str
    density: Decimal
    reference_sfd: Length
    points: tuple[ChartPoint, ...]

    def __post_init__(self) -> None:
        if len(self.points) < 2:
            raise ConfigurationError("a chart needs at least two points", key=self.key)
        thicknesses = [p.thickness for p in self.points]
        if thicknesses != sorted(thicknesses):
            raise ConfigurationError("chart points are out of order", key=self.key)
        if len(set(thicknesses)) != len(thicknesses):
            raise ConfigurationError("a chart has two points at one thickness", key=self.key)
        exposures = [p.curie_minutes for p in self.points]
        if exposures != sorted(exposures):
            raise ConfigurationError("a chart curve has to rise", key=self.key)

    @property
    def thinnest(self) -> Length:
        return self.points[0].thickness

    @property
    def thickest(self) -> Length:
        return self.points[-1].thickness

    def covers(self, thickness: Length) -> bool:
        return self.thinnest <= thickness <= self.thickest

    def factor_for(self, thickness: Length) -> Decimal:
        """Curie minutes at the reference distance, film and density."""
        if not self.covers(thickness):
            raise TechniqueError(
                "that thickness is off the end of the chart",
                chart=self.key,
                thickness_mm=str(thickness.mm),
                from_mm=str(self.thinnest.mm),
                to_mm=str(self.thickest.mm),
            )
        lower = self.points[0]
        for point in self.points:
            if point.thickness == thickness:
                return point.curie_minutes
            if point.thickness > thickness:
                return _interpolate(lower, point, thickness)
            lower = point
        return lower.curie_minutes

    def thickness_for(self, curie_minutes: Decimal) -> Length:
        """The other way up the chart, for working out what a shot can reach."""
        if curie_minutes <= 0:
            raise ValidationError("an exposure has to be positive")
        first, last = self.points[0], self.points[-1]
        if not first.curie_minutes <= curie_minutes <= last.curie_minutes:
            raise TechniqueError(
                "that exposure is off the end of the chart",
                chart=self.key,
                curie_minutes=str(curie_minutes),
            )
        lower = first
        for point in self.points:
            if point.curie_minutes == curie_minutes:
                return point.thickness
            if point.curie_minutes > curie_minutes:
                return _invert(lower, point, curie_minutes)
            lower = point
        return lower.thickness


def _interpolate(low: ChartPoint, high: ChartPoint, thickness: Length) -> Decimal:
    """Linear in the logarithm, which is what the semi log paper does."""
    with localcontext() as ctx:
        ctx.prec = 28
        span = Decimal(high.thickness.um - low.thickness.um)
        offset = Decimal(thickness.um - low.thickness.um)
        low_log = low.curie_minutes.ln()
        high_log = high.curie_minutes.ln()
        return (low_log + (high_log - low_log) * offset / span).exp()


def _invert(low: ChartPoint, high: ChartPoint, curie_minutes: Decimal) -> Length:
    with localcontext() as ctx:
        ctx.prec = 28
        low_log = low.curie_minutes.ln()
        high_log = high.curie_minutes.ln()
        fraction = (curie_minutes.ln() - low_log) / (high_log - low_log)
        span = Decimal(high.thickness.um - low.thickness.um)
        return Length(int(Decimal(low.thickness.um) + span * fraction))


def chart_from_inches(
    key: str,
    isotope_key: str,
    material_key: str,
    film_key: str,
    density: str,
    reference_sfd_inches: str,
    readings: tuple[tuple[str, str], ...],
) -> ExposureChart:
    """Build a chart from the numbers as they are written on the card."""
    return ExposureChart(
        key=key,
        isotope_key=isotope_key,
        material_key=material_key,
        film_key=film_key,
        density=Decimal(density),
        reference_sfd=Length.from_inch(reference_sfd_inches),
        points=tuple(ChartPoint(Length.from_inch(t), Decimal(e)) for t, e in readings),
    )
