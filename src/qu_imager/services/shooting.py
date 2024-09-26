"""Recording that a shot was made, and what came out of it.

Three things happen and they happen in one transaction. A number is taken. A
radiograph row is written with that number. The admissibility verdict is stored
against it. Splitting them is what leaves a hole in the numbering, and the hole
is not recoverable afterwards because nobody can prove whether the missing
number was a lost film or a rolled back transaction.

The service will not record a shot against a weld that already has that
exposure index. That is not a database nicety. Two films both labelled exposure
two on the same weld means one of them is a reshoot that somebody filed as an
original, and the difference matters to the repair rate and to the coverage
check.

Admissibility is computed here rather than being passed in. The caller supplies
the evidence, which is what the reader measured, and the rules decide. A
caller that can pass in the verdict is a caller that can pass in the wrong one.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.admissibility import Admissibility, Evidence, assess
from qu_imager.persistence.models.work import RadiographRow
from qu_imager.persistence.unit_of_work import UnitOfWork


@dataclass(frozen=True, slots=True)
class ShotRecord:
    """One exposure, as the crew reports it."""

    weld: WeldId
    exposure_index: int
    exposed_at: datetime
    technique_reference: str
    evidence: Evidence

    def __post_init__(self) -> None:
        if self.exposure_index < 1:
            raise ValidationError("exposures are numbered from one")
        if not self.technique_reference.strip():
            raise ValidationError("a shot names the technique it was made to")


@dataclass(frozen=True, slots=True)
class ShotResult:
    """What the service issued and decided."""

    number: str
    admissibility: Admissibility

    @property
    def needs_reshooting(self) -> bool:
        return not self.admissibility.admissible


def record_shot(work: UnitOfWork, job: JobNumber, record: ShotRecord) -> ShotResult:
    """Number the shot, store it, and say whether it is admissible."""
    job_row = work.jobs.get(job)
    weld_row = work.welds.get(job_row, record.weld)

    taken = [r.exposure_index for r in weld_row.radiographs]
    if record.exposure_index in taken:
        raise ConflictError(
            "that exposure is already recorded on this weld",
            weld=str(record.weld),
            exposure=record.exposure_index,
        )

    verdict = assess(record.evidence)
    number = work.numbering.take_radiograph_number(str(job))
    weld_row.radiographs.append(
        RadiographRow(
            number=number,
            exposure_index=record.exposure_index,
            exposed_at=record.exposed_at,
            technique_reference=record.technique_reference,
            medium=record.evidence.system.medium.value,
            admissible=verdict.admissible,
            inadmissible_reason=None if verdict.admissible else "; ".join(verdict.reasons),
            density_through_indicator=(
                record.evidence.density.indicator_density
                if record.evidence.density is not None
                else None
            ),
            normalised_snr=(
                record.evidence.noise.normalised if record.evidence.noise is not None else None
            ),
        )
    )
    work.flush()
    return ShotResult(number=number, admissibility=verdict)


def exposures_recorded(work: UnitOfWork, job: JobNumber, weld: WeldId) -> tuple[int, ...]:
    """Which exposures are already on a weld, in order."""
    job_row = work.jobs.get(job)
    weld_row = work.welds.get(job_row, weld)
    return tuple(sorted(r.exposure_index for r in weld_row.radiographs))


def missing_exposures(
    work: UnitOfWork, job: JobNumber, weld: WeldId, expected: int
) -> tuple[int, ...]:
    """The exposure numbers that have not been shot yet."""
    if expected < 1:
        raise ValidationError("a weld needs at least one exposure")
    done = set(exposures_recorded(work, job, weld))
    return tuple(index for index in range(1, expected + 1) if index not in done)
