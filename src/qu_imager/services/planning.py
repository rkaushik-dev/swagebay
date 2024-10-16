"""Planning a shot from what is on the record rather than from what is typed.

The value of doing this against the database instead of against arguments is
that the wall, the diameter and the reinforcement come off the weld row, and
the source activity comes off the certificate decayed to the day. Nobody
retypes any of it, so nobody mistypes any of it, and a sheet planned in the
office matches the weld the crew is standing in front of.

What still has to be said is the part that is a choice: which source, which
film, which shot, and what night. Those are decisions and they are arguments.

A plan is returned whether or not it is clean. A sheet with warnings on it is
what a Level III reads and signs off, and hiding it behind an exception would
mean the only way to see the warnings is to trigger them, which is backwards.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.film import film as film_system
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.imaging.system import ImagingSystem
from qu_imager.persistence.source_mapper import row_to_source
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.persistence.work_mapper import row_to_joint
from qu_imager.quantities.duration import Duration
from qu_imager.technique import TechniqueRequest, TechniqueSheet, plan_technique


@dataclass(frozen=True, slots=True)
class PlanRequest:
    """The decisions that are not on the record."""

    weld: WeldId
    source_serial: str
    film_key: str
    shot_at: datetime
    shot: ShotType | None = None
    indicator_side: IndicatorSide = IndicatorSide.SOURCE
    window: Duration | None = None
    density: Decimal = Decimal("2.0")

    def __post_init__(self) -> None:
        if not self.source_serial.strip():
            raise ValidationError("a plan names the source it is planned for")
        if not self.film_key.strip():
            raise ValidationError("a plan names the film it is planned for")

    def on_film(self, film_key: str) -> PlanRequest:
        """The same plan on a different film, for comparing."""
        return PlanRequest(
            weld=self.weld,
            source_serial=self.source_serial,
            film_key=film_key,
            shot_at=self.shot_at,
            shot=self.shot,
            indicator_side=self.indicator_side,
            window=self.window,
            density=self.density,
        )


def plan(work: UnitOfWork, job: JobNumber, request: PlanRequest) -> TechniqueSheet:
    """Read the weld and the source off the record, then plan the shot."""
    job_row = work.jobs.get(job)
    weld_row = work.welds.get(job_row, request.weld)
    source_row = work.sources.get(request.source_serial)

    chosen = film_system(request.film_key)
    return plan_technique(
        TechniqueRequest(
            joint=row_to_joint(weld_row),
            source=row_to_source(source_row),
            system=ImagingSystem.from_film(chosen),
            film=chosen,
            shot_at=request.shot_at,
            shot=request.shot,
            indicator_side=request.indicator_side,
            window=request.window,
            density=request.density,
        )
    )


def compare_films(
    work: UnitOfWork, job: JobNumber, request: PlanRequest, films: tuple[str, ...]
) -> dict[str, TechniqueSheet]:
    """The same shot on several films, which is how a shooter picks one."""
    if not films:
        raise ValidationError("there is nothing to compare")
    return {key: plan(work, job, request.on_film(key)) for key in films}


def fastest_workable(
    work: UnitOfWork, job: JobNumber, request: PlanRequest, films: tuple[str, ...]
) -> str | None:
    """The film that gives the shortest clean sheet, or nothing if none is clean."""
    sheets = compare_films(work, job, request, films)
    workable = {key: sheet for key, sheet in sheets.items() if sheet.is_clean}
    if not workable:
        return None
    return min(workable, key=lambda k: workable[k].total_source_out.ms)
