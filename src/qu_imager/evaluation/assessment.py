"""Applying the acceptance table to what the reader found.

Four checks run against each kind present, and which of them run is decided by
the row rather than by the code here. That is the point of keeping the table
separate: adding a rule to the standard means adding a field, not adding a
branch.

The individual check asks whether any one imperfection is over its own cap. The
width check does the same across the weld rather than along it. The aggregate
check slides the twelve inch window to its worst position and compares the
total. The count check does the same but counts instead of measuring.

On a weld shorter than the window the aggregate check is replaced rather than
skipped. That is a real difference and it catches out anybody who assumes short
welds are judged more leniently. A percentage of a short weld is a small number.

Cracks are handled before any of it. There is no arithmetic that makes a crack
acceptable, so the only question is whether it is the one exception the code
allows, a shallow crater crack under four millimetres, and everything else is
rejected on sight.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.evaluation.aggregate import (
    STANDARD_WINDOW,
    count_in_worst_window,
    fraction_of_weld,
    window_applies,
    worst_window,
)
from qu_imager.evaluation.api1104 import criterion, undercut_depth_limit
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length


class Disposition(Enum):
    ACCEPT = "accept"
    REPAIR = "repair"
    REJECT = "reject"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Finding:
    """One rule that was broken, with the clause and the numbers."""

    clause: str
    kind: Kind
    reason: str
    indications: tuple[Indication, ...] = ()

    def describe(self) -> str:
        return f"{self.clause} {self.kind.code}: {self.reason}"


@dataclass(frozen=True, slots=True)
class Assessment:
    """What the code says about one weld."""

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
            return "accept"
        return "; ".join(f.describe() for f in self.findings)


def assess(found: IndicationSet, wall: Length) -> Assessment:
    """Run every check the table calls for and collect the findings."""
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")

    findings: list[Finding] = []
    for kind in sorted(found.kinds(), key=lambda k: k.name):
        findings.extend(_check_kind(found, kind, wall))
    findings.extend(_check_undercut_depth(found, wall))

    disposition = Disposition.ACCEPT
    if findings:
        disposition = (
            Disposition.REJECT
            if any(f.kind.is_always_rejectable for f in findings)
            else Disposition.REPAIR
        )
    return Assessment(disposition=disposition, findings=tuple(findings), wall=wall)


def _check_kind(found: IndicationSet, kind: Kind, wall: Length) -> list[Finding]:
    row = criterion(kind)
    present = found.of_kind(kind)
    out: list[Finding] = []

    limit = row.individual_length_limit(wall)
    if limit is not None:
        for indication in present:
            if indication.size > limit:
                out.append(
                    Finding(
                        clause=row.clause,
                        kind=kind,
                        reason=(
                            f"{indication.size.format_mm(1)} is over the "
                            f"{limit.format_mm(1)} allowed for one"
                        ),
                        indications=(indication,),
                    )
                )

    if row.max_width is not None:
        for indication in present:
            if indication.width > row.max_width:
                out.append(
                    Finding(
                        clause=row.clause,
                        kind=kind,
                        reason=(
                            f"{indication.width.format_mm(1)} across is over the "
                            f"{row.max_width.format_mm(1)} allowed"
                        ),
                        indications=(indication,),
                    )
                )

    if row.max_aggregate is not None and kind.is_aggregated:
        out.extend(
            _check_aggregate(found, kind, row.clause, row.max_aggregate, row.short_weld_fraction)
        )

    if row.max_count is not None:
        count = count_in_worst_window(present, found.weld_length)
        if count > row.max_count:
            out.append(
                Finding(
                    clause=row.clause,
                    kind=kind,
                    reason=f"{count} in one window is over the {row.max_count} allowed",
                    indications=present,
                )
            )
    return out


def _check_aggregate(
    found: IndicationSet,
    kind: Kind,
    clause: str,
    allowed: Length,
    short_weld_fraction: Decimal | None,
) -> list[Finding]:
    present = found.of_kind(kind)
    if not present:
        return []
    if window_applies(found.weld_length):
        window = worst_window(present, found.weld_length)
        if window.aggregate > allowed:
            return [
                Finding(
                    clause=clause,
                    kind=kind,
                    reason=(
                        f"{window.describe()} is over the {allowed.format_mm(1)} allowed "
                        f"in {STANDARD_WINDOW.format_mm(0)}"
                    ),
                    indications=present,
                )
            ]
        return []
    if short_weld_fraction is None:
        return []
    total = found.total_length_of(kind)
    fraction = fraction_of_weld(total, found.weld_length)
    if fraction > short_weld_fraction:
        return [
            Finding(
                clause=clause,
                kind=kind,
                reason=(
                    f"{total.format_mm(1)} is {fraction:.1%} of a short weld, over "
                    f"{short_weld_fraction:.0%}"
                ),
                indications=present,
            )
        ]
    return []


def _check_undercut_depth(found: IndicationSet, wall: Length) -> list[Finding]:
    limit = undercut_depth_limit(wall)
    out: list[Finding] = []
    for indication in found.of_kind(Kind.U):
        if indication.depth_fraction is None:
            continue
        depth = wall * indication.depth_fraction
        if depth > limit:
            out.append(
                Finding(
                    clause="9.3.11",
                    kind=Kind.U,
                    reason=(f"{depth.format_mm(2)} deep is over the {limit.format_mm(2)} allowed"),
                    indications=(indication,),
                )
            )
    return out
