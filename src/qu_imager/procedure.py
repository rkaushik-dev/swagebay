"""Procedures and their revisions, resolved as of a date.

Everything the shop does is done to a written procedure, and the procedure gets
revised. That would be unremarkable except that a radiograph shot in March has
to stay defensible in a review three years later, and the question the reviewer
asks is not what the procedure says now. It is what the procedure said that
morning.

So revisions are never edited and never deleted. A new revision supersedes an
old one from its effective date forward, and the old one stays resolvable
forever. A register that answers only for today is a register that loses every
argument with a client.

The other thing worth being strict about is approval. A revision is not
effective because somebody typed it. It is effective because a Level III signed
it, on a date, and the signature is part of the record. A revision with no
approval is a draft and the resolver will not return it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.personnel import Person


class Code(Enum):
    """The code the procedure is written to."""

    ASME_V = "ASME Section V"
    API_1104 = "API 1104"
    ISO_17636 = "ISO 17636-1"
    ASME_VIII = "ASME Section VIII Division 1"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Revision:
    """One revision of one procedure, as approved."""

    number: str
    revision: int
    title: str
    code: Code
    effective_on: date
    approved_by: Person | None = None
    approved_on: date | None = None

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise ValidationError("a procedure needs a number")
        if self.revision < 0:
            raise ValidationError("a revision number is not negative", revision=self.revision)
        if (self.approved_by is None) != (self.approved_on is None):
            raise ValidationError(
                "an approval needs both a signature and a date", number=self.number
            )
        if self.approved_on is not None and self.approved_on > self.effective_on:
            raise ValidationError(
                "a revision cannot take effect before it was approved",
                approved_on=self.approved_on.isoformat(),
                effective_on=self.effective_on.isoformat(),
            )

    @property
    def is_approved(self) -> bool:
        return self.approved_by is not None

    @property
    def label(self) -> str:
        return f"{self.number} rev {self.revision}"

    def approval_is_sound(self) -> bool:
        """Whether the signature was one the signer was entitled to give."""
        if self.approved_by is None or self.approved_on is None:
            return False
        return self.approved_by.may_approve_procedure_on(self.approved_on)

    def __str__(self) -> str:
        return f"{self.label} ({self.code})"


@dataclass(slots=True)
class ProcedureRegister:
    """Every revision of every procedure, answerable as of any date."""

    revisions: list[Revision]

    def __post_init__(self) -> None:
        seen: set[tuple[str, int]] = set()
        for revision in self.revisions:
            key = (revision.number, revision.revision)
            if key in seen:
                raise ValidationError("the same revision is registered twice", label=revision.label)
            seen.add(key)
        self.revisions = sorted(
            self.revisions, key=lambda r: (r.number, r.effective_on, r.revision)
        )

    def add(self, revision: Revision) -> None:
        self.revisions = [*self.revisions, revision]
        self.__post_init__()

    def history(self, number: str) -> tuple[Revision, ...]:
        return tuple(r for r in self.revisions if r.number == number)

    def effective_on(self, number: str, day: date) -> Revision:
        """The revision that governed work done that day."""
        candidates = [r for r in self.history(number) if r.is_approved and r.effective_on <= day]
        if not candidates:
            raise ConfigurationError(
                "no approved revision of that procedure was in force",
                number=number,
                day=day.isoformat(),
            )
        return max(candidates, key=lambda r: (r.effective_on, r.revision))

    def current(self, number: str, today: date) -> Revision:
        return self.effective_on(number, today)

    def is_superseded(self, revision: Revision, day: date) -> bool:
        return self.effective_on(revision.number, day) != revision

    def numbers(self) -> tuple[str, ...]:
        return tuple(sorted({r.number for r in self.revisions}))
