"""Jobs over http.

Every handler is four lines and that is the target. Parse the path parameter
into the domain type, call the service, shape the result. Anything longer than
that means a decision has leaked out of the service and into the transport,
and the first sign of it is always a handler with an if in it.

The job number arrives as a string in the path and is parsed by the same
grammar the rest of the package uses, so a malformed number is refused with the
same message a malformed number in a spreadsheet import gets. That is worth
more than a neat 404, because the caller learns what shape the thing should
have been.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    JobOut,
    LengthOut,
    NewJobIn,
    NewWeldIn,
    OutstandingOut,
    WeldOut,
)
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.identity import JobNumber, WeldId
from qu_imager.materials import material
from qu_imager.naming import code_named
from qu_imager.persistence.models.work import JobRow, WeldRow
from qu_imager.quantities.length import Length
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, close_job, open_job, outstanding

router = APIRouter(prefix="/jobs", tags=["jobs"])


@router.post("", status_code=status.HTTP_201_CREATED)
def create_job(body: NewJobIn, work: Work) -> JobOut:
    number = open_job(
        work,
        NewJob(
            client=body.client,
            site=body.site,
            code=code_named(body.acceptance_code),
            procedure_number=body.procedure_number,
            opened_on=body.opened_on,
        ),
    )
    return _job_out(work.jobs.get(number))


@router.get("/{number}")
def read_job(number: str, work: Work) -> JobOut:
    return _job_out(work.jobs.get(JobNumber.parse(number)))


@router.get("")
def list_jobs(work: Work, on: date | None = None) -> list[JobOut]:
    day = on or date.today()
    return [_job_out(row) for row in work.jobs.open_on(day)]


@router.post("/{number}/welds", status_code=status.HTTP_201_CREATED)
def create_weld(number: str, body: NewWeldIn, work: Work) -> WeldOut:
    job = JobNumber.parse(number)
    add_weld(
        work,
        job,
        NewWeld(
            weld=WeldId.parse(body.weld),
            joint=_joint(body),
            weld_length=body.weld_length.to_length(),
            welder=body.welder,
            welded_on=body.welded_on,
        ),
    )
    return _weld_out(work.welds.get(work.jobs.get(job), WeldId.parse(body.weld)))


@router.get("/{number}/welds")
def list_welds(number: str, work: Work) -> list[WeldOut]:
    job = work.jobs.get(JobNumber.parse(number))
    return [_weld_out(row) for row in sorted(job.welds, key=lambda w: (w.number, w.repair_round))]


@router.get("/{number}/outstanding")
def read_outstanding(number: str, work: Work) -> OutstandingOut:
    job = JobNumber.parse(number)
    return OutstandingOut(job=str(job), items=outstanding(work, job))


@router.post("/{number}/close")
def close(number: str, work: Work, on: date | None = None) -> JobOut:
    job = JobNumber.parse(number)
    close_job(work, job, on or date.today())
    return _job_out(work.jobs.get(job))


def _joint(body: NewWeldIn) -> Joint:
    diameter = body.joint.outside_diameter
    reinforcement = body.joint.reinforcement
    return Joint(
        form=JointForm.PLATE_BUTT if diameter is None else JointForm.PIPE_GIRTH,
        wall=body.joint.wall.to_length(),
        material=material(body.joint.material),
        outside_diameter=diameter.to_length() if diameter is not None else None,
        reinforcement=(reinforcement.to_length() if reinforcement is not None else Length.ZERO),
    )


def _job_out(row: JobRow) -> JobOut:
    return JobOut(
        number=str(JobNumber(row.year, row.serial)),
        client=row.client,
        site=row.site,
        acceptance_code=row.acceptance_code,
        procedure_number=row.procedure_number,
        opened_on=row.opened_on,
        closed_on=row.closed_on,
        welds=len(row.welds),
    )


def _weld_out(row: WeldRow) -> WeldOut:
    return WeldOut(
        weld=str(WeldId(row.prefix, row.number, row.repair_round)),
        material=row.material_key,
        wall=LengthOut.of(row.wall),
        outside_diameter=(
            LengthOut.of(row.outside_diameter) if row.outside_diameter is not None else None
        ),
        reinforcement=LengthOut.of(row.reinforcement),
        weld_length=LengthOut.of(row.weld_length),
        welder=row.welder,
        welded_on=row.welded_on,
        radiographs=len(row.radiographs),
    )
