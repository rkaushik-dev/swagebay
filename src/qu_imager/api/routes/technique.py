"""Technique sheets over http.

The shot type is optional in the request and that is the useful shape. Left
out, the planner picks the best shot the geometry allows, which is what a
coordinator wants when they are laying out a job. Named, the planner uses it,
which is what a shooter wants when the site has already decided that the source
is not going inside anything.

The comparison endpoint exists because picking a film is a real decision made
several times a night, and doing it by calling the planner four times from a
phone is how it gets done wrong.
"""

from __future__ import annotations

from fastapi import APIRouter

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    ActivityOut,
    CoverageOut,
    DurationOut,
    LengthOut,
    PlanIn,
    SheetOut,
)
from qu_imager.errors import ValidationError
from qu_imager.identity import JobNumber, WeldId
from qu_imager.naming import optional_shot_named, side_named
from qu_imager.quantities.duration import Duration
from qu_imager.services.planning import PlanRequest, compare_films, fastest_workable, plan
from qu_imager.technique import TechniqueSheet

router = APIRouter(prefix="/jobs/{number}/welds/{weld}", tags=["technique"])


@router.post("/technique")
def plan_sheet(number: str, weld: str, body: PlanIn, work: Work) -> SheetOut:
    job = JobNumber.parse(number)
    identity = WeldId.parse(weld)
    sheet = plan(work, job, _request(identity, body))
    return _sheet_out(identity, sheet)


@router.post("/technique/compare")
def compare(number: str, weld: str, body: PlanIn, work: Work, films: str) -> list[SheetOut]:
    job = JobNumber.parse(number)
    identity = WeldId.parse(weld)
    keys = _films(films)
    sheets = compare_films(work, job, _request(identity, body), keys)
    return [_sheet_out(identity, sheets[key]) for key in keys]


@router.post("/technique/fastest")
def fastest(number: str, weld: str, body: PlanIn, work: Work, films: str) -> dict[str, str | None]:
    job = JobNumber.parse(number)
    identity = WeldId.parse(weld)
    return {"film": fastest_workable(work, job, _request(identity, body), _films(films))}


def _films(films: str) -> tuple[str, ...]:
    keys = tuple(k.strip() for k in films.split(",") if k.strip())
    if not keys:
        raise ValidationError("name at least one film to compare", films=films)
    return keys


def _request(weld: WeldId, body: PlanIn) -> PlanRequest:
    return PlanRequest(
        weld=weld,
        source_serial=body.source_serial,
        film_key=body.film,
        shot_at=body.shot_at,
        shot=optional_shot_named(body.shot),
        indicator_side=side_named(body.indicator_side),
        window=(
            Duration.from_minutes(body.window_minutes) if body.window_minutes is not None else None
        ),
        density=body.density,
    )


def _sheet_out(weld: WeldId, sheet: TechniqueSheet) -> SheetOut:
    return SheetOut(
        weld=str(weld),
        shot=sheet.shot.value,
        penetrated=LengthOut.of(sheet.penetrated),
        source_to_film=LengthOut.of(sheet.source_to_film),
        geometric_unsharpness=LengthOut.of(sheet.geometry.geometric_unsharpness),
        indicator=sheet.indicator.describe(),
        coverage=CoverageOut(
            exposures=sheet.coverage.exposures,
            separation_degrees=sheet.coverage.separation_degrees,
            overlap_degrees=sheet.coverage.overlap_degrees,
        ),
        exposure=DurationOut.of(sheet.exposure.exposure),
        total_source_out=DurationOut.of(sheet.total_source_out),
        activity=ActivityOut.of(sheet.exposure.activity),
        warnings=sheet.warnings,
        clean=sheet.is_clean,
    )
