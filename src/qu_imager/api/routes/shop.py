"""The operational endpoints: the morning, the checklist and the doctor.

None of these belong to one record, which is why they are not hanging off an
existing router. They answer questions about the whole shop on a day, and a
dispatcher's screen refreshing every few minutes is the only caller any of them
will ever have.

All three are gets and none of them writes. The doctor in particular reads a
lot and changes nothing, so it is safe to point a health check at it, and the
response says how many checks ran rather than just whether it was happy.
"""

from __future__ import annotations

from datetime import date
from typing import Annotated

from fastapi import APIRouter, Query

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    ChecklistOut,
    DoctorFindingOut,
    DoctorOut,
    MorningOut,
)
from qu_imager.identity import JobNumber
from qu_imager.services.closeout import checklist
from qu_imager.services.doctor import check_names, examine
from qu_imager.services.morning import films_waiting, loads_ready, sheet_for

router = APIRouter(tags=["shop"])


@router.get("/morning")
def read_morning(work: Work, on: date | None = None, loads: bool = False) -> MorningOut:
    day = on or date.today()
    sheet = sheet_for(work, day)
    return MorningOut(
        day=day,
        stops_today=tuple(line.describe() for line in sheet.stops_today),
        due_this_week=tuple(line.describe() for line in sheet.stops_this_week),
        running_late=tuple(line.describe() for line in sheet.running_late),
        films_waiting=films_waiting(work, day),
        loads_ready=tuple(f"{d} with {s}" for d, s in loads_ready(work, day)) if loads else (),
        quiet=sheet.is_quiet,
    )


@router.get("/jobs/{number}/checklist")
def read_checklist(number: str, work: Work) -> ChecklistOut:
    found = checklist(work, JobNumber.parse(number))
    return ChecklistOut(
        job=number,
        clear=found.is_clear,
        work_reasons=found.work_reasons,
        report_reasons=found.report_reasons,
        image_reasons=found.image_reasons,
    )


@router.get("/doctor")
def read_doctor(
    work: Work,
    check: Annotated[list[str] | None, Query()] = None,
) -> DoctorOut:
    found = examine(work, tuple(check or ()))
    return DoctorOut(
        checks_run=found.checks_run,
        checks_available=check_names(),
        healthy=found.is_healthy,
        findings=tuple(
            DoctorFindingOut(check=f.check, subject=f.subject, account=f.account)
            for f in found.findings
        ),
    )
