"""One door in front of two codes that do not agree about anything.

A job is worked to one acceptance code and the code is a property of the job,
not of the weld. The same weld, with the same imperfections, in the same wall,
is accepted under one and rejected under the other, and that is not a defect in
either of them. They were written for different things: API 1104 for a pipeline
that will be pressure tested and then buried, UW-51 for a vessel that will be
in service with people standing next to it.

What this module does is pick, and record what it picked. What it does not do
is average, reconcile or apply both. A finding that quotes a clause from one
code and a limit from the other is worthless, and the way that gets written is
by having one function that tries to be clever about which rule is stricter.

The unified verdict keeps the code and the clause on every finding, so a report
reads the same whichever was used and an auditor can always tell which one was.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.evaluation import asme_uw51
from qu_imager.evaluation.assessment import Assessment, Disposition, Finding, assess
from qu_imager.evaluation.indication import IndicationSet
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length


class Code(Enum):
    API_1104 = "API 1104"
    ASME_VIII = "ASME Section VIII Division 1"

    @property
    def judges_by_kind(self) -> bool:
        """API has a rule per imperfection. UW-51 has a rule per shape."""
        return self is Code.API_1104

    @property
    def window_moves_with_the_wall(self) -> bool:
        return self is Code.ASME_VIII

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Verdict:
    """What one code says about one weld, with the code on the record."""

    code: Code
    disposition: Disposition
    findings: tuple[Finding, ...]
    wall: Length

    @property
    def accepted(self) -> bool:
        return self.disposition is Disposition.ACCEPT

    def clauses(self) -> tuple[str, ...]:
        return tuple(sorted({f.clause for f in self.findings}))

    def describe(self) -> str:
        if self.accepted:
            return f"accept to {self.code}"
        reasons = "; ".join(f.describe() for f in self.findings)
        return f"{self.code}: {reasons}"


def judge(code: Code, found: IndicationSet, wall: Length) -> Verdict:
    """Apply exactly one code, and say which one it was."""
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")
    if code is Code.API_1104:
        return _from_api(assess(found, wall), wall)
    if code is Code.ASME_VIII:
        return _from_asme(found, wall)
    raise ConfigurationError("no evaluator for that code", code=code.value)


def _from_api(assessment: Assessment, wall: Length) -> Verdict:
    return Verdict(
        code=Code.API_1104,
        disposition=assessment.disposition,
        findings=assessment.findings,
        wall=wall,
    )


def _from_asme(found: IndicationSet, wall: Length) -> Verdict:
    raw = asme_uw51.evaluate(found.indications, wall)
    findings = tuple(
        Finding(
            clause=f"UW-51({item.rule})",
            kind=item.indications[0].kind if item.indications else Kind.C,
            reason=item.reason,
            indications=item.indications,
        )
        for item in raw
    )
    disposition = Disposition.ACCEPT
    if findings:
        disposition = (
            Disposition.REJECT
            if any(f.kind.is_always_rejectable for f in findings)
            else Disposition.REPAIR
        )
    return Verdict(code=Code.ASME_VIII, disposition=disposition, findings=findings, wall=wall)


def codes_disagree(found: IndicationSet, wall: Length) -> bool:
    """Whether the two codes reach different dispositions on the same weld.

    Kept because it is the question a client asks when they change the
    specification halfway through a job, and because it is worth being able to
    answer without running both by hand.
    """
    api = judge(Code.API_1104, found, wall).disposition
    asme = judge(Code.ASME_VIII, found, wall).disposition
    return api is not asme
