"""Reports over http, including the csv a client's system pulls.

The csv endpoints return text rather than json and they say so in the media
type. A client integration that expects csv and gets a json string containing
csv has to unwrap it, and every one of them gets the unwrapping wrong the first
time.

The package endpoint returns the faults even when there are none, as an empty
list rather than by leaving the field out. A field that appears only when
something is wrong is a field an integration forgets to check.
"""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import PlainTextResponse

from qu_imager.api.app import Work
from qu_imager.api.schemas import PackageOut, WeldSummaryOut
from qu_imager.identity import JobNumber
from qu_imager.reporting.exports import sheets, to_text
from qu_imager.reporting.package import assemble
from qu_imager.reporting.traceability import broken_chains

router = APIRouter(prefix="/jobs/{number}/report", tags=["reports"])


@router.get("")
def read_package(number: str, work: Work) -> PackageOut:
    job = JobNumber.parse(number)
    package = assemble(work, job)
    return PackageOut(
        job=str(package.job),
        client=package.client,
        site=package.site,
        code=package.code.value,
        procedure=package.procedure_number,
        opened_on=package.opened_on,
        closed_on=package.closed_on,
        complete=package.is_complete,
        faults=package.faults,
        welds_shot=package.welds_shot,
        welds_repaired=package.welds_repaired,
        repair_rate=package.repair_rate().value,
        repair_rate_reportable=package.repair_rate().is_reportable,
        radiographs=len(package.radiographs),
        welds=tuple(
            WeldSummaryOut(
                weld=line.weld,
                wall_mm=line.wall.mm,
                welder=line.welder,
                radiographs=line.radiographs,
                disposition=line.disposition,
                clauses=line.clauses,
            )
            for line in package.welds
        ),
    )


@router.get("/traceability")
def read_traceability(number: str, work: Work) -> dict[str, list[str]]:
    job = JobNumber.parse(number)
    broken = broken_chains(work, job.year, job.serial)
    return {chain.radiograph: [link.describe() for link in chain.broken()] for chain in broken}


@router.get("/{sheet}.csv", response_class=PlainTextResponse)
def read_csv(number: str, sheet: str, work: Work) -> str:
    package = assemble(work, JobNumber.parse(number))
    return to_text(package, sheet)


@router.get("/sheets")
def list_sheets() -> list[str]:
    return list(sheets())
