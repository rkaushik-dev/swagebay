"""Recording surveys, and refusing to lose the one that matters.

Both surveys on an exposure go in the same record and neither is optional. The
before survey confirms the area was clear. The after survey confirms the source
went home. A shift with twelve exposures and eleven after surveys has one
exposure nobody can prove ended safely, and the missing one is never the one
anybody remembers.

So this service asks the record which exposures are missing a survey rather
than trusting a crew to notice. That query is the whole reason surveys are
stored against a radiograph number instead of against a shift.

A failing after survey is recorded and then reported loudly. It is not refused.
A survey that reads three hundred millirem at the device is a fact about the
world, and software that will not let a crew write it down is software that
gets a crew writing it on paper instead.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

from sqlalchemy import select

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.models.safety import SurveyPointRow, SurveyRow
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.safety.survey import SURFACE_READING_CEILING, SurveyStage


@dataclass(frozen=True, slots=True)
class Point:
    """One reading, as the surveyor took it."""

    label: str
    distance: Length
    bearing_degrees: int
    reading: DoseRate
    at_device_surface: bool = False

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("a survey point has a label")
        if self.distance.um <= 0:
            raise ValidationError("a survey point is somewhere", label=self.label)


@dataclass(frozen=True, slots=True)
class SurveyOutcome:
    """What recording a survey established."""

    stage: SurveyStage
    faults: tuple[str, ...]
    hottest_label: str
    hottest_reading: DoseRate

    @property
    def is_clear(self) -> bool:
        return not self.faults


def record_survey(
    work: UnitOfWork,
    radiograph_number: str,
    stage: SurveyStage,
    performed_at: datetime,
    performed_by: str,
    instrument: str,
    calibration_due: datetime,
    points: tuple[Point, ...],
) -> SurveyOutcome:
    """Store a survey and say what is wrong with it, without refusing it."""
    if not points:
        raise ValidationError("a survey with no readings is not a survey")
    if not performed_by.strip():
        raise ValidationError("a survey is signed")

    row = SurveyRow(
        radiograph_number=radiograph_number,
        stage=stage.name.lower(),
        performed_at=performed_at,
        performed_by=performed_by,
        instrument=instrument,
        calibration_due=calibration_due,
    )
    for point in points:
        row.points.append(
            SurveyPointRow(
                label=point.label,
                distance=point.distance,
                bearing_degrees=point.bearing_degrees,
                reading=point.reading,
                at_device_surface=point.at_device_surface,
            )
        )
    work.session.add(row)
    work.flush()

    hottest = max(points, key=lambda p: p.reading.nsv_per_hour)
    return SurveyOutcome(
        stage=stage,
        faults=_faults(stage, performed_at, calibration_due, points),
        hottest_label=hottest.label,
        hottest_reading=hottest.reading,
    )


def _faults(
    stage: SurveyStage,
    performed_at: datetime,
    calibration_due: datetime,
    points: tuple[Point, ...],
) -> tuple[str, ...]:
    out: list[str] = []
    if performed_at >= calibration_due:
        out.append("the survey meter calibration has expired")
    if stage is SurveyStage.AFTER:
        surface = next((p for p in points if p.at_device_surface), None)
        if surface is None:
            out.append("no reading was taken at the device surface")
        elif surface.reading > SURFACE_READING_CEILING:
            out.append(
                f"the device surface reads {surface.reading.format_mr_per_hour()}, "
                "the source is not fully retracted"
            )
    return tuple(out)


def surveys_for(work: UnitOfWork, radiograph_number: str) -> tuple[SurveyRow, ...]:
    return tuple(
        work.session.scalars(
            select(SurveyRow)
            .where(SurveyRow.radiograph_number == radiograph_number)
            .order_by(SurveyRow.performed_at)
        ).all()
    )


def missing_surveys(work: UnitOfWork, radiograph_numbers: tuple[str, ...]) -> tuple[str, ...]:
    """Every exposure that is short of a before or an after survey."""
    out: list[str] = []
    for number in radiograph_numbers:
        stages = {row.stage for row in surveys_for(work, number)}
        for wanted in ("before", "after"):
            if wanted not in stages:
                out.append(f"{number} has no {wanted} survey")
    return tuple(out)


def stuck_source_reports(work: UnitOfWork) -> tuple[str, ...]:
    """Every after survey whose surface reading says the source did not go home."""
    rows = work.session.scalars(select(SurveyRow).where(SurveyRow.stage == "after")).all()
    out: list[str] = []
    for row in rows:
        surface = next((p for p in row.points if p.at_device_surface), None)
        if surface is not None and surface.reading > SURFACE_READING_CEILING:
            out.append(
                f"{row.radiograph_number}: {surface.reading.format_mr_per_hour()} at the device"
            )
    return tuple(out)


def scatter_factor(measured: DoseRate, calculated: DoseRate) -> Decimal:
    """How far the meter and the model differ, which the office trends."""
    if calculated.nsv_per_hour <= 0:
        raise ValidationError("a calculated rate has to be positive")
    return Decimal(measured.nsv_per_hour) / Decimal(calculated.nsv_per_hour)


def require_clear(outcome: SurveyOutcome) -> None:
    """For the callers that genuinely cannot proceed on a faulty survey."""
    if not outcome.is_clear:
        raise ConflictError("the survey is not clear", faults="; ".join(outcome.faults))
