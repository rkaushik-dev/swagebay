"""Recording what a reader saw, and reaching a disposition for the weld.

Reading is where the software has the least to say and the most to be careful
about. It does not decide what is on the film. A qualified person does that,
and this records what they decided, who they were and when.

What it does enforce is the three things a reading is not allowed to be. It is
not allowed to be done by somebody whose certification or eye exam has lapsed,
because a report signed by them is worthless and the shop would rather find
that out now than in review. It is not allowed to be recorded twice, because a
second reading of the same film is a rereading and it belongs on the record as
one, with both readers named. And it is not allowed to be recorded against a
film nobody has established is admissible, because indications read off an
inadmissible film are not evidence of anything.

The weld disposition falls out of the readings rather than being entered. It is
computed from every indication on every admissible film covering the weld, run
through whichever code the job is worked to.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime

from qu_imager.audit import Action, Entry
from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.acceptance import Verdict, judge
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.identity import JobNumber, WeldId
from qu_imager.naming import code_named
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.persistence.work_mapper import indication_to_row, row_to_indication
from qu_imager.personnel import Person


@dataclass(frozen=True, slots=True)
class ReadingRecord:
    """One reader, one film, and what they wrote down."""

    radiograph_number: str
    reader: Person
    read_at: datetime
    indications: tuple[Indication, ...] = ()

    def __post_init__(self) -> None:
        if not self.radiograph_number.strip():
            raise ValidationError("a reading names the film it was made on")


def record_reading(work: UnitOfWork, record: ReadingRecord) -> None:
    """Store a reading, refusing the three things a reading cannot be."""
    row = work.welds.radiograph(record.radiograph_number)

    if not record.reader.may_interpret_on(record.read_at.date()):
        raise ConflictError(
            "that reader may not interpret film on that day",
            badge=record.reader.badge,
            lapsed=", ".join(record.reader.lapsed_on(record.read_at.date())),
        )
    if row.read_at is not None:
        raise ConflictError(
            "that film has already been read",
            number=record.radiograph_number,
            by=row.read_by,
        )
    if not row.admissible:
        raise ConflictError(
            "an inadmissible film is not evidence of anything",
            number=record.radiograph_number,
        )

    row.read_by = record.reader.badge
    row.read_at = record.read_at
    for indication in record.indications:
        row.indications.append(indication_to_row(indication))
    work.flush()


@dataclass(frozen=True, slots=True)
class RereadRequest:
    """A second reading of the same film, by somebody else, on the record."""

    radiograph_number: str
    reader: Person
    read_at: datetime
    reason: str
    indications: tuple[Indication, ...] = ()

    def __post_init__(self) -> None:
        if not self.radiograph_number.strip():
            raise ValidationError("a reread names the film it was made on")
        if not self.reason.strip():
            raise ValidationError("a reread says why it was asked for")


def record_reread(work: UnitOfWork, request: RereadRequest) -> None:
    """Store a second reading beside the first, never over it.

    A client who disagrees with a call asks for another reader to look at the
    same film. What comes back is a second opinion and the record has to hold
    both, because the interesting case is the one where they differ and a
    record that keeps only the later one hides exactly that.

    The second reader's indications become the ones the disposition is computed
    from, since a reread exists to correct the first pass. What the first pass
    said is not lost. It goes on the audit trail as the old value, with the
    reason the reread was asked for.
    """
    row = work.welds.radiograph(request.radiograph_number)

    if row.read_at is None:
        raise ConflictError(
            "nothing has been read on that film yet", number=request.radiograph_number
        )
    if row.reread_at is not None:
        raise ConflictError(
            "that film has already been reread",
            number=request.radiograph_number,
            by=row.reread_by,
        )
    if row.read_by == request.reader.badge:
        raise ConflictError(
            "a reread is done by somebody other than the first reader",
            badge=request.reader.badge,
        )
    if request.read_at < row.read_at:
        raise ConflictError(
            "a reread comes after the reading it revisits",
            number=request.radiograph_number,
        )
    if not request.reader.may_interpret_on(request.read_at.date()):
        raise ConflictError(
            "that reader may not interpret film on that day",
            badge=request.reader.badge,
            lapsed=", ".join(request.reader.lapsed_on(request.read_at.date())),
        )

    was = _describe_indications(tuple(row_to_indication(i) for i in row.indications))
    became = _describe_indications(request.indications)

    row.reread_by = request.reader.badge
    row.reread_at = request.read_at
    row.reread_reason = request.reason
    row.indications.clear()
    for indication in request.indications:
        row.indications.append(indication_to_row(indication))

    if was != became:
        work.audit.changed(
            request.read_at,
            request.reader.badge,
            "radiograph",
            request.radiograph_number,
            "indications",
            was,
            became,
            request.reason,
        )
    else:
        work.audit.append(
            Entry(
                at=request.read_at,
                actor=request.reader.badge,
                action=Action.SIGNED,
                record="radiograph",
                identity=request.radiograph_number,
                reason=request.reason,
            )
        )
    work.flush()


def _describe_indications(found: tuple[Indication, ...]) -> str:
    """What a reader wrote down, in one line, so the trail can hold it."""
    if not found:
        return "nothing"
    return "; ".join(sorted(i.describe() for i in found))


def readers_of(work: UnitOfWork, number: str) -> tuple[str, ...]:
    """Every person who has read one film, in the order they read it."""
    row = work.welds.radiograph(number)
    return tuple(badge for badge in (row.read_by, row.reread_by) if badge is not None)


def disputed(work: UnitOfWork, job: JobNumber) -> tuple[str, ...]:
    """Films two readers looked at, which are the ones a review asks about."""
    job_row = work.jobs.get(job)
    found: list[str] = []
    for weld_row in job_row.welds:
        found.extend(r.number for r in weld_row.radiographs if r.reread_at is not None)
    return tuple(sorted(found))


def weld_verdict(work: UnitOfWork, job: JobNumber, weld: WeldId) -> Verdict | None:
    """The disposition, or nothing while any film is unread or inadmissible."""
    job_row = work.jobs.get(job)
    weld_row = work.welds.get(job_row, weld)
    if not weld_row.radiographs:
        return None
    if any(not r.admissible or r.read_at is None for r in weld_row.radiographs):
        return None
    found = tuple(row_to_indication(i) for r in weld_row.radiographs for i in r.indications)
    return judge(
        code_named(job_row.acceptance_code),
        IndicationSet(weld_row.weld_length, found),
        weld_row.wall,
    )


def readings_outstanding(work: UnitOfWork, job: JobNumber) -> tuple[str, ...]:
    """Every film on the job still waiting for a reader."""
    job_row = work.jobs.get(job)
    return tuple(r.number for r in work.welds.unread(job_row))


def readers_available(crew: tuple[Person, ...], day: date) -> tuple[Person, ...]:
    return tuple(p for p in crew if p.may_interpret_on(day))
