"""Surveys, which are the only part of this package that touches reality.

Every number on the safety side is a calculation until somebody walks the
boundary with a meter. The survey is where the model is checked, and it is
checked twice on every exposure: before, to confirm the area is clear and the
boundary is where the sketch says, and after, to confirm the source went back
into the device.

The second one is the one that matters. A source that hangs up in the guide
tube reads normal at the boundary, because the boundary is a long way away and
the difference between a retracted source and a stuck one is a factor a
hundred metres of air has already swallowed. It reads wrong at the device
surface, which is why the post exposure survey is taken there, on the way past,
every single time, and why a survey record with no surface reading on it is not
a survey record.

A reading that disagrees with the calculation is not automatically a bad
reading. Air scatters, concrete scatters back, the meter has an energy
response, and the source is not a point. What the office wants to know is which
direction the disagreement runs. Measured hotter than calculated is a finding.
Measured colder is a note.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.safety.field import BoundaryKind, RadiationField

SURFACE_READING_CEILING = DoseRate.from_mr_per_hour(200)
"""Above this at the device surface the source did not go home."""

TOLERANCE = Decimal("1.5")
"""A reading up to half again the calculation is put down to scatter."""


class SurveyStage(Enum):
    BEFORE = "before the exposure"
    AFTER = "after the exposure"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class SurveyPoint:
    """One reading, where it was taken and which way it faced."""

    label: str
    distance: Length
    bearing_degrees: Decimal
    reading: DoseRate
    at_device_surface: bool = False

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("a survey point needs a label")
        if self.distance.um <= 0:
            raise ValidationError("a survey point is somewhere", label=self.label)


@dataclass(frozen=True, slots=True)
class SurveyRecord:
    """One walk round with a meter."""

    stage: SurveyStage
    performed_at: datetime
    performed_by: str
    instrument: str
    instrument_calibration_due: datetime
    points: tuple[SurveyPoint, ...]

    def __post_init__(self) -> None:
        if not self.points:
            raise ValidationError("a survey with no readings is not a survey")
        if not self.performed_by.strip():
            raise ValidationError("a survey is signed")

    @property
    def instrument_is_current(self) -> bool:
        return self.performed_at < self.instrument_calibration_due

    def surface_reading(self) -> SurveyPoint | None:
        for point in self.points:
            if point.at_device_surface:
                return point
        return None

    def hottest(self) -> SurveyPoint:
        return max(self.points, key=lambda p: p.reading.nsv_per_hour)


@dataclass(frozen=True, slots=True)
class Discrepancy:
    """One point where the meter and the model disagree."""

    point: SurveyPoint
    calculated: DoseRate
    ratio: Decimal

    @property
    def measured_hotter(self) -> bool:
        return self.ratio > 1

    def describe(self) -> str:
        direction = "hotter" if self.measured_hotter else "colder"
        return (
            f"{self.point.label} reads {self.point.reading.format_mr_per_hour()}, "
            f"{self.ratio.quantize(Decimal('0.01'))} times the calculated "
            f"{self.calculated.format_mr_per_hour()}, {direction}"
        )


def compare(record: SurveyRecord, radiation_field: RadiationField) -> tuple[Discrepancy, ...]:
    """Every point where the meter and the model differ by more than scatter."""
    out: list[Discrepancy] = []
    for point in record.points:
        if point.at_device_surface:
            continue
        calculated = radiation_field.rate_at(point.distance, point.bearing_degrees)
        if calculated.nsv_per_hour == 0:
            continue
        ratio = Decimal(point.reading.nsv_per_hour) / Decimal(calculated.nsv_per_hour)
        if ratio > TOLERANCE or ratio < 1 / TOLERANCE:
            out.append(Discrepancy(point=point, calculated=calculated, ratio=ratio))
    return tuple(out)


def faults(record: SurveyRecord) -> tuple[str, ...]:
    """Everything wrong with the survey itself, before any comparison."""
    out: list[str] = []
    if not record.instrument_is_current:
        out.append("the survey meter calibration has expired")
    surface = record.surface_reading()
    if record.stage is SurveyStage.AFTER:
        if surface is None:
            out.append("no reading was taken at the device surface")
        elif surface.reading > SURFACE_READING_CEILING:
            out.append(
                f"the device surface reads {surface.reading.format_mr_per_hour()}, "
                "the source is not fully retracted"
            )
    return tuple(out)


def boundary_confirmed(record: SurveyRecord, kind: BoundaryKind = BoundaryKind.RESTRICTED) -> bool:
    """Whether every reading outside the device is under the boundary rate."""
    return all(point.reading <= kind.rate for point in record.points if not point.at_device_surface)
