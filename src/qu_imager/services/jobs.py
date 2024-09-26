"""Opening a job, adding welds to it, and closing it.

A service is a use case with a transaction round it, and that is all it is. It
does not compute anything the domain can compute, and it does not query
anything the repositories can query. What it owns is the order things happen
in and what happens when one of them fails.

Opening a job takes the next serial for the year and writes the job in the same
transaction, which is the only way the serial cannot be handed out twice.
Adding a weld checks the job is open first, because a weld added to a closed
job is a weld nobody will look for again.

Closing a job is the one with a real rule in it. A job closes when every weld
on it has a disposition, and a weld with an unread radiograph does not have
one. So closing reports what is outstanding rather than refusing with a bare
no, because the coordinator closing the job needs the list.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry.joint import Joint
from qu_imager.identity import JobNumber, WeldId
from qu_imager.persistence.models.work import JobRow, WeldRow
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.persistence.work_mapper import joint_to_row
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class NewJob:
    """What has to be known before a job can be opened."""

    client: str
    site: str
    code: Code
    procedure_number: str
    opened_on: date

    def __post_init__(self) -> None:
        for name, value in (
            ("client", self.client),
            ("site", self.site),
            ("procedure_number", self.procedure_number),
        ):
            if not value.strip():
                raise ValidationError("a job needs this filled in", field=name)


@dataclass(frozen=True, slots=True)
class NewWeld:
    """A weld to add to a job."""

    weld: WeldId
    joint: Joint
    weld_length: Length
    welder: str
    welded_on: date

    def __post_init__(self) -> None:
        if not self.welder.strip():
            raise ValidationError("a weld has a welder")
        if self.weld_length.um <= 0:
            raise ValidationError("a weld has a length")


def open_job(work: UnitOfWork, request: NewJob) -> JobNumber:
    """Take the next serial for the year and write the job with it."""
    jobs = work.jobs
    year = request.opened_on.year % 100
    serial = jobs.next_serial(year)
    number = JobNumber(year=year, serial=serial)
    jobs.add(
        JobRow(
            year=number.year,
            serial=number.serial,
            client=request.client,
            site=request.site,
            acceptance_code=request.code.value,
            procedure_number=request.procedure_number,
            opened_on=request.opened_on,
        )
    )
    work.flush()
    return number


def add_weld(work: UnitOfWork, number: JobNumber, request: NewWeld) -> None:
    """Add a weld to an open job."""
    jobs = work.jobs
    welds = work.welds
    job = jobs.get(number)
    if job.closed_on is not None:
        raise ConflictError("that job is closed", job=str(number))
    if welds.find(job, request.weld) is not None:
        raise ConflictError("that weld is already on the job", weld=str(request.weld))
    row = WeldRow(
        prefix=request.weld.prefix,
        number=request.weld.number,
        repair_round=request.weld.repair,
        material_key=request.joint.material.key,
        wall=request.joint.wall,
        reinforcement=request.joint.reinforcement,
        weld_length=request.weld_length,
        welder=request.welder,
        welded_on=request.welded_on,
    )
    joint_to_row(request.joint, request.weld, row)
    row.weld_length = request.weld_length
    row.welder = request.welder
    row.welded_on = request.welded_on
    job.welds.append(row)
    work.flush()


def outstanding(work: UnitOfWork, number: JobNumber) -> tuple[str, ...]:
    """Everything standing between the job and being closed."""
    jobs = work.jobs
    welds = work.welds
    job = jobs.get(number)
    out: list[str] = []
    if not job.welds:
        out.append("the job has no welds on it")
    for radiograph in welds.inadmissible(job):
        out.append(f"{radiograph.number} is not admissible")
    for radiograph in welds.unread(job):
        out.append(f"{radiograph.number} has not been read")
    for weld in job.welds:
        if not weld.radiographs:
            out.append(f"GW-{weld.number} has no radiographs")
    return tuple(out)


def close_job(work: UnitOfWork, number: JobNumber, on: date) -> None:
    """Close a job, refusing while anything is outstanding."""
    jobs = work.jobs
    job = jobs.get(number)
    if job.closed_on is not None:
        raise ConflictError("that job is already closed", job=str(number))
    if on < job.opened_on:
        raise ValidationError("a job cannot close before it opened", job=str(number))
    remaining = outstanding(work, number)
    if remaining:
        raise ConflictError("the job is not finished", job=str(number), outstanding=len(remaining))
    job.closed_on = on
    work.flush()
