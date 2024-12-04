"""Safety over http: where the rope goes, and what the meter said.

The boundary endpoint takes the source and what is round it in the request
rather than reading it off the inventory, because the crew asking is standing
next to a projector with a collimator they may or may not have fitted, and the
inventory does not know that. The endpoint answers the question as asked.

The survey endpoint stores what the meter read and hands back the faults. It
does not refuse a bad survey, for the reason written in the service: a survey
the software will not accept is a survey that ends up on paper.
"""

from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    BoundaryIn,
    BoundaryOut,
    LengthOut,
    RopeOut,
    SurveyIn,
    SurveyOut,
)
from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import DoseRate
from qu_imager.safety.field import BoundaryKind, RadiationField
from qu_imager.safety.shielding import Barrier
from qu_imager.safety.survey import SurveyStage
from qu_imager.services.surveys import Point, record_survey
from qu_imager.sources.device import collimator
from qu_imager.sources.isotope import isotope

router = APIRouter(prefix="/safety", tags=["safety"])

DOWN = Decimal(0)
SIDE = Decimal(180)


@router.post("/boundaries")
def boundaries(body: BoundaryIn) -> RopeOut:
    field = _field(body)
    out = [
        BoundaryOut(
            kind=kind.value,
            rate_mr_per_hour=kind.rate.mr_per_hour,
            down_beam=LengthOut.of(field.boundary(kind, DOWN)),
            off_beam=LengthOut.of(field.boundary(kind, SIDE)),
        )
        for kind in BoundaryKind
    ]
    return RopeOut(
        boundaries=tuple(out),
        directional=any(b.down_beam.um != b.off_beam.um for b in out),
    )


@router.post("/radiographs/{radiograph}/surveys", status_code=status.HTTP_201_CREATED)
def survey(radiograph: str, body: SurveyIn, work: Work) -> SurveyOut:
    outcome = record_survey(
        work,
        radiograph,
        SurveyStage.BEFORE if body.stage == "before" else SurveyStage.AFTER,
        body.performed_at,
        body.performed_by,
        body.instrument,
        body.calibration_due,
        tuple(
            Point(
                label=point.label,
                distance=point.distance.to_length(),
                bearing_degrees=point.bearing_degrees,
                reading=DoseRate.from_mr_per_hour(point.mr_per_hour),
                at_device_surface=point.at_device_surface,
            )
            for point in body.points
        ),
    )
    return SurveyOut(
        radiograph=radiograph,
        stage=body.stage,
        clear=outcome.is_clear,
        faults=outcome.faults,
        hottest_label=outcome.hottest_label,
        hottest_mr_per_hour=outcome.hottest_reading.mr_per_hour,
    )


def _field(body: BoundaryIn) -> RadiationField:
    barriers: tuple[Barrier, ...] = ()
    if body.barrier_material is not None:
        if body.barrier_thickness is None:
            raise ValidationError("a barrier needs a thickness")
        barriers = (Barrier(body.barrier_material, body.barrier_thickness.to_length()),)
    return RadiationField(
        isotope=isotope(body.isotope),
        activity=Activity.from_curie(body.curies),
        collimator=collimator(body.collimator) if body.collimator is not None else None,
        barriers=barriers,
    )
