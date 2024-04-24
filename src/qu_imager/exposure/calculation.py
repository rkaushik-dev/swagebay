"""Turning a chart reading into a number of seconds on a timer.

The last step is a division and it is the one everybody can do in their head:

    minutes = corrected curie minutes / curies in the pig

What makes it worth writing down is the two bounds either side of it.

A shot shorter than about ten seconds is not a shot. The source spends a few
seconds travelling out the guide tube and a few coming back, and during both it
is exposing the film from somewhere other than where it is supposed to be. The
shorter the exposure the bigger that error is as a fraction, and under ten
seconds the density is not reproducible. The fix is a slower film, a smaller
source or a longer distance, and the software says so rather than printing four
seconds and letting the crew find out.

A shot longer than the window is not a shot either. If the client gives seven
hours of clear time and the weld needs nine, no amount of good intent makes it
work, and finding that out on site rather than in the office costs a night.

Both bounds are advisory in the sense that a Level III can override them, and
neither is advisory in the sense that they can be left out of the record.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.exposure.chart import ExposureChart
from qu_imager.exposure.corrections import CorrectionChain
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length

MINIMUM_PRACTICAL = Duration.from_seconds(10)
"""Below this the transit of the source is a big enough fraction to show."""


@dataclass(frozen=True, slots=True)
class ExposureResult:
    """The whole calculation, kept so the sheet can print it."""

    thickness: Length
    chart_key: str
    chart_reading: Decimal
    corrections: CorrectionChain
    corrected_reading: Decimal
    activity: Activity
    exposure: Duration
    warnings: tuple[str, ...] = ()

    @property
    def timer(self) -> str:
        return self.exposure.as_timer()

    @property
    def is_practical(self) -> bool:
        return not self.warnings

    def describe(self) -> str:
        return (
            f"{self.chart_key} at {self.thickness.format_mm()} reads "
            f"{self.chart_reading.normalize()} Ci min, "
            f"{self.corrections.describe()}, "
            f"{self.activity.format_curie()} gives {self.timer}"
        )


def exposure_time(corrected_curie_minutes: Decimal, activity: Activity) -> Duration:
    """The division, on its own, for callers that already have the numbers."""
    if corrected_curie_minutes <= 0:
        raise ValidationError("a corrected reading has to be positive")
    if activity.bq <= 0:
        raise TechniqueError("an empty source cannot make an exposure")
    minutes = corrected_curie_minutes / activity.curie
    return Duration.from_minutes(minutes).round_up_to_second()


def plan_exposure(
    chart: ExposureChart,
    thickness: Length,
    corrections: CorrectionChain,
    activity: Activity,
    window: Duration | None = None,
) -> ExposureResult:
    """Read the chart, apply the chain, divide by the curies, check the bounds."""
    reading = chart.factor_for(thickness)
    corrected = corrections.applied_to(reading)
    span = exposure_time(corrected, activity)

    warnings: list[str] = []
    if span < MINIMUM_PRACTICAL:
        warnings.append(
            f"exposure of {span.as_timer()} is under the ten second floor, "
            "use a slower film or a longer distance"
        )
    if window is not None and span > window:
        warnings.append(
            f"exposure of {span.as_timer()} does not fit the {window.as_timer()} window"
        )

    return ExposureResult(
        thickness=thickness,
        chart_key=chart.key,
        chart_reading=reading,
        corrections=corrections,
        corrected_reading=corrected,
        activity=activity,
        exposure=span,
        warnings=tuple(warnings),
    )


def activity_needed(corrected_curie_minutes: Decimal, target: Duration) -> Activity:
    """How strong a source would make that shot in that time."""
    if target.ms <= 0:
        raise ValidationError("a target exposure has to be positive")
    if corrected_curie_minutes <= 0:
        raise ValidationError("a corrected reading has to be positive")
    return Activity.from_curie(corrected_curie_minutes / target.minutes)


def total_for_exposures(single: Duration, count: int) -> Duration:
    """The source is out this long over the whole joint, which is what safety wants."""
    if count <= 0:
        raise ValidationError("a joint needs at least one exposure", count=count)
    return Duration(single.ms * count)
