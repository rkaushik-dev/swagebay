"""The record of one radiograph, and of the weld it belongs to.

Everything else in this package computes. This is where the results are pinned
to a piece of paper with a number on it, and the number is what a client, an
auditor or a court will ask for in four years.

A radiograph record carries three things that are easy to conflate and must not
be. What was intended, which is the technique sheet it was shot to. What
happened, which is the exposure that was actually made and when. And what was
read off it, which is the reader, the date they read it, and the indications
they wrote down. A record that carries only the third is a record nobody can
defend, because there is no way to show the film was made the way the procedure
says.

A weld record gathers the radiographs that cover one weld and reaches a
disposition for the weld as a whole. The whole is not the sum: a weld with four
sound radiographs and one inadmissible one is not four fifths examined, it is
not examined, because the fifth of it nobody could read might be where the
crack is.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.acceptance import Code, Verdict, judge
from qu_imager.evaluation.assessment import Disposition
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.admissibility import Admissibility
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Radiograph:
    """One image, what it was shot to, and what was read off it."""

    number: str
    weld: WeldId
    exposure_index: int
    exposed_at: datetime
    technique_reference: str
    admissibility: Admissibility
    indications: tuple[Indication, ...] = ()
    read_by: str = ""
    read_at: datetime | None = None

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise ValidationError("a radiograph needs a number")
        if self.exposure_index < 1:
            raise ValidationError("exposures are numbered from one", number=self.number)
        if not self.technique_reference.strip():
            raise ValidationError(
                "a radiograph names the technique it was shot to", number=self.number
            )
        if (self.read_by.strip() == "") != (self.read_at is None):
            raise ValidationError("a reading has both a reader and a date", number=self.number)
        if self.indications and self.read_at is None:
            raise ValidationError("indications were written down by somebody", number=self.number)

    @property
    def is_admissible(self) -> bool:
        return self.admissibility.admissible

    @property
    def has_been_read(self) -> bool:
        return self.read_at is not None

    def describe(self) -> str:
        if not self.is_admissible:
            return f"{self.number} not admissible: {self.admissibility.describe()}"
        if not self.has_been_read:
            return f"{self.number} admissible, not yet read"
        return f"{self.number} read by {self.read_by}, {len(self.indications)} indications"


@dataclass(slots=True)
class WeldRecord:
    """Every radiograph covering one weld, and where that leaves the weld."""

    job: JobNumber
    weld: WeldId
    weld_length: Length
    wall: Length
    code: Code
    radiographs: list[Radiograph] = field(default_factory=list)

    def add(self, radiograph: Radiograph) -> None:
        if radiograph.weld != self.weld:
            raise ValidationError(
                "that radiograph belongs to another weld",
                expected=str(self.weld),
                given=str(radiograph.weld),
            )
        if any(r.number == radiograph.number for r in self.radiographs):
            raise ConflictError("that radiograph number is already on the weld")
        if any(r.exposure_index == radiograph.exposure_index for r in self.radiographs):
            raise ConflictError(
                "that exposure is already on the weld", index=radiograph.exposure_index
            )
        self.radiographs.append(radiograph)

    @property
    def inadmissible(self) -> tuple[Radiograph, ...]:
        return tuple(r for r in self.radiographs if not r.is_admissible)

    @property
    def unread(self) -> tuple[Radiograph, ...]:
        return tuple(r for r in self.radiographs if r.is_admissible and not r.has_been_read)

    def all_indications(self) -> IndicationSet:
        found: list[Indication] = []
        for radiograph in self.radiographs:
            if radiograph.is_admissible:
                found.extend(radiograph.indications)
        return IndicationSet(self.weld_length, tuple(found))

    def verdict(self, expected_exposures: int) -> Verdict | None:
        """The weld level verdict, or nothing while the coverage is incomplete."""
        if self.is_incomplete(expected_exposures):
            return None
        return judge(self.code, self.all_indications(), self.wall)

    def is_incomplete(self, expected_exposures: int) -> bool:
        if expected_exposures < 1:
            raise ValidationError("a weld needs at least one exposure")
        if len(self.radiographs) < expected_exposures:
            return True
        return bool(self.inadmissible or self.unread)

    def blockers(self, expected_exposures: int) -> tuple[str, ...]:
        """Why there is no verdict yet, in the words a coordinator would use."""
        out: list[str] = []
        missing = expected_exposures - len(self.radiographs)
        if missing > 0:
            out.append(f"{missing} of {expected_exposures} exposures are not shot")
        for radiograph in self.inadmissible:
            out.append(f"{radiograph.number} is not admissible and has to be reshot")
        for radiograph in self.unread:
            out.append(f"{radiograph.number} has not been read")
        return tuple(out)

    def disposition(self, expected_exposures: int) -> Disposition | None:
        verdict = self.verdict(expected_exposures)
        return verdict.disposition if verdict is not None else None
