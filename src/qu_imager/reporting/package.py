"""The report package, which is the thing the client actually pays for.

A job produces a stack of paper and the stack has to be complete, internally
consistent and defensible on its own without anybody being available to explain
it. That is a higher bar than the software needs to work, and the gap between
the two is where most disputes live.

Complete means every weld on the job has a disposition, every disposition is
supported by radiographs that were admissible, and every radiograph names the
technique it was shot to. Missing any one of those makes the package
unfinishable rather than merely incomplete, and this reports which.

Consistent means the numbers agree with each other. The count of radiographs on
the summary equals the number of radiograph lines. The repair rate equals the
repaired welds over the welds shot. Somebody will check, and finding it wrong
costs more than the whole job.

The package is built rather than stored. Anything stored can drift from the
record it summarises, and a summary that disagrees with its own detail is worse
than no summary.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from qu_imager.evaluation.acceptance import Code, Verdict, judge
from qu_imager.evaluation.indication import IndicationSet
from qu_imager.identity import JobNumber, WeldId
from qu_imager.naming import code_named
from qu_imager.persistence.models.work import WeldRow
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.persistence.work_mapper import row_to_indication
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class RadiographLine:
    """One line of the radiograph schedule."""

    number: str
    weld: str
    exposure_index: int
    exposed_on: date
    technique_reference: str
    medium: str
    admissible: bool
    read_by: str | None
    indications: int


@dataclass(frozen=True, slots=True)
class WeldLine:
    """One line of the weld summary."""

    weld: str
    wall: Length
    welder: str
    radiographs: int
    disposition: str
    clauses: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class Package:
    """Everything the client is owed, assembled from the record."""

    job: JobNumber
    client: str
    site: str
    code: Code
    procedure_number: str
    opened_on: date
    closed_on: date | None
    welds: tuple[WeldLine, ...]
    radiographs: tuple[RadiographLine, ...]
    faults: tuple[str, ...]

    @property
    def is_complete(self) -> bool:
        return not self.faults

    @property
    def welds_shot(self) -> int:
        return len([w for w in self.welds if w.radiographs > 0])

    @property
    def welds_repaired(self) -> int:
        return len({w.weld.split("R")[0] for w in self.welds if "R" in w.weld})

    def repair_rate(self) -> Decimal:
        if self.welds_shot == 0:
            return Decimal(0)
        return Decimal(self.welds_repaired) / Decimal(self.welds_shot)

    def counts_agree(self) -> bool:
        """The summary and the detail have to add up to the same thing."""
        summed = sum(w.radiographs for w in self.welds)
        return summed == len(self.radiographs)


def assemble(work: UnitOfWork, number: JobNumber) -> Package:
    """Build the package from the record, and say what is missing."""
    row = work.jobs.get(number)
    code = code_named(row.acceptance_code)

    weld_lines: list[WeldLine] = []
    radiograph_lines: list[RadiographLine] = []
    faults: list[str] = []

    for weld in sorted(row.welds, key=lambda w: (w.number, w.repair_round)):
        identity = str(WeldId(weld.prefix, weld.number, weld.repair_round))
        verdict = _verdict(weld, code)
        if verdict is None:
            faults.append(f"{identity} has no disposition")
        for film in sorted(weld.radiographs, key=lambda r: r.exposure_index):
            if not film.admissible:
                faults.append(f"{film.number} is not admissible")
            if film.read_at is None:
                faults.append(f"{film.number} has not been read")
            if not film.technique_reference.strip():
                faults.append(f"{film.number} names no technique")
            radiograph_lines.append(
                RadiographLine(
                    number=film.number,
                    weld=identity,
                    exposure_index=film.exposure_index,
                    exposed_on=film.exposed_at.date(),
                    technique_reference=film.technique_reference,
                    medium=film.medium,
                    admissible=film.admissible,
                    read_by=film.read_by,
                    indications=len(film.indications),
                )
            )
        if not weld.radiographs:
            faults.append(f"{identity} has no radiographs")
        weld_lines.append(
            WeldLine(
                weld=identity,
                wall=weld.wall,
                welder=weld.welder,
                radiographs=len(weld.radiographs),
                disposition=verdict.disposition.value if verdict else "none",
                clauses=verdict.clauses() if verdict else (),
            )
        )

    if not row.welds:
        faults.append("the job has no welds on it")

    return Package(
        job=number,
        client=row.client,
        site=row.site,
        code=code,
        procedure_number=row.procedure_number,
        opened_on=row.opened_on,
        closed_on=row.closed_on,
        welds=tuple(weld_lines),
        radiographs=tuple(radiograph_lines),
        faults=tuple(faults),
    )


def _verdict(weld: WeldRow, code: Code) -> Verdict | None:
    films = weld.radiographs
    if not films:
        return None
    if any(not f.admissible or f.read_at is None for f in films):
        return None
    found = tuple(row_to_indication(i) for f in films for i in f.indications)
    return judge(code, IndicationSet(weld.weld_length, found), weld.wall)
