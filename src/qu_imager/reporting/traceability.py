"""The traceability chain, which is the question a review actually asks.

Nobody in a review asks whether the software worked. They pick one radiograph
out of a stack of four hundred and ask what it was shot with, to which
procedure revision, by whom, and whether all of those were valid on the night.
Answering that from four separate records by hand takes twenty minutes a film
and it is the single most expensive hour of any audit.

The chain is built rather than stored, and each link carries whether it holds.
A link that cannot be established is reported as broken rather than being left
out, because a chain with a link quietly missing looks complete.

The links are deliberately not all the same shape. Some are facts about the
record, like which technique a film names. Some are judgements about a date,
like whether the procedure revision was in force. Keeping them apart means a
reviewer can see which are transcription and which are reasoning.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from qu_imager.errors import NotFoundError
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.procedure import ProcedureRegister


@dataclass(frozen=True, slots=True)
class Link:
    """One step of the chain, with whether it holds."""

    name: str
    value: str
    holds: bool
    note: str = ""

    def describe(self) -> str:
        state = "ok" if self.holds else "broken"
        detail = f" ({self.note})" if self.note else ""
        return f"{self.name}: {self.value} [{state}]{detail}"


@dataclass(frozen=True, slots=True)
class Chain:
    """Everything behind one radiograph."""

    radiograph: str
    links: tuple[Link, ...]

    @property
    def is_sound(self) -> bool:
        return all(link.holds for link in self.links)

    def broken(self) -> tuple[Link, ...]:
        return tuple(link for link in self.links if not link.holds)

    def describe(self) -> str:
        lines = [f"{self.radiograph}"]
        lines.extend(f"  {link.describe()}" for link in self.links)
        return "\n".join(lines)


def chain_for(
    work: UnitOfWork, radiograph_number: str, register: ProcedureRegister | None = None
) -> Chain:
    """Build the chain behind one radiograph."""
    film = work.welds.radiograph(radiograph_number)
    weld = film.weld
    job = weld.job
    shot_on: date = film.exposed_at.date()

    links: list[Link] = [
        Link("job", f"{job.year:02d}-{job.serial:04d}", True),
        Link("weld", f"{weld.prefix}-{weld.number}", True),
        Link("welder", weld.welder, bool(weld.welder.strip())),
        Link("exposed", shot_on.isoformat(), True),
        Link(
            "technique",
            film.technique_reference,
            bool(film.technique_reference.strip()),
            "" if film.technique_reference.strip() else "no technique named",
        ),
        Link(
            "admissible",
            "yes" if film.admissible else "no",
            film.admissible,
            film.inadmissible_reason or "",
        ),
        Link(
            "read by",
            film.read_by or "nobody",
            film.read_at is not None,
            "" if film.read_at is not None else "not yet read",
        ),
        _procedure_link(job.procedure_number, shot_on, register),
    ]
    return Chain(radiograph=radiograph_number, links=tuple(links))


def _procedure_link(number: str, shot_on: date, register: ProcedureRegister | None) -> Link:
    if register is None:
        return Link("procedure", number, True, "revision not checked, no register given")
    try:
        revision = register.effective_on(number, shot_on)
    except Exception:  # noqa: BLE001 - any refusal means the link does not hold
        return Link("procedure", number, False, "no approved revision was in force")
    return Link("procedure", revision.label, True)


def chains_for_job(
    work: UnitOfWork, year: int, serial: int, register: ProcedureRegister | None = None
) -> tuple[Chain, ...]:
    """Every chain on one job, in radiograph order."""
    from qu_imager.identity import JobNumber

    job = work.jobs.get(JobNumber(year, serial))
    numbers = sorted(film.number for weld in job.welds for film in weld.radiographs)
    return tuple(chain_for(work, number, register) for number in numbers)


def broken_chains(
    work: UnitOfWork, year: int, serial: int, register: ProcedureRegister | None = None
) -> tuple[Chain, ...]:
    return tuple(c for c in chains_for_job(work, year, serial, register) if not c.is_sound)


def assert_traceable(work: UnitOfWork, radiograph_number: str) -> Chain:
    """Build the chain and refuse if it does not hold."""
    chain = chain_for(work, radiograph_number)
    if not chain.is_sound:
        raise NotFoundError("intact traceability chain", radiograph_number)
    return chain
