"""ASME Section VIII UW-51, which is a different shape of rule entirely.

A pipeline crew that has only ever worked to API 1104 gets caught by this the
first time they shoot a vessel, because the two codes disagree about almost
everything except cracks.

API judges each kind of imperfection against its own table. UW-51 does not care
what caused the imperfection at all. It asks one question about shape, planar
or rounded, and then judges the planar ones purely on length against a limit
that slides with the wall.

The limit is a three part function. Under three quarters of an inch of wall it
is a flat quarter inch. Above two and a quarter inches it is a flat three
quarters. Between them it is a third of the wall, and the two flat sections
join the sloping one exactly at the ends, which is worth checking in a test
because a transcription error there is invisible on any single thickness.

Alignment is the other difference. UW-51 groups indications that line up and
totals them over a window of twelve wall thicknesses, not twelve inches, so the
window itself moves with the wall. And a group breaks whenever the gap between
two of its members exceeds six times the longest one, which means a group is
not simply everything inside the window.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.evaluation.indication import Indication
from qu_imager.quantities.length import Length

THIN_LIMIT = Length.from_inch("0.75")
THICK_LIMIT = Length.from_inch("2.25")
SHORT_ALLOWANCE = Length.from_inch("0.25")
LONG_ALLOWANCE = Length.from_inch("0.75")
WINDOW_IN_WALLS = 12
GROUP_GAP_MULTIPLE = 6


def elongated_limit(wall: Length) -> Length:
    """The longest single planar indication UW-51 allows at that wall."""
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")
    if wall <= THIN_LIMIT:
        return SHORT_ALLOWANCE
    if wall >= THICK_LIMIT:
        return LONG_ALLOWANCE
    return wall / 3


def window_for(wall: Length) -> Length:
    """Twelve wall thicknesses, which is the window this code totals over."""
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")
    return wall * WINDOW_IN_WALLS


@dataclass(frozen=True, slots=True)
class Group:
    """Indications the code treats as one for the aggregate check."""

    members: tuple[Indication, ...]

    @property
    def aggregate(self) -> Length:
        total = Length.ZERO
        for member in self.members:
            total = total + member.length
        return total

    @property
    def longest(self) -> Length:
        return max((m.length for m in self.members), default=Length.ZERO)

    @property
    def span(self) -> Length:
        if not self.members:
            return Length.ZERO
        return self.members[-1].ends_at - self.members[0].starts_at

    def __len__(self) -> int:
        return len(self.members)


def group_aligned(indications: tuple[Indication, ...]) -> tuple[Group, ...]:
    """Break a run of indications wherever the gap exceeds six times the longest.

    The break rule reads backwards at first. The gap is compared against the
    longest indication in the group so far, so one long imperfection makes the
    group harder to break and pulls more of its neighbours in with it.
    """
    ordered = sorted(indications, key=lambda i: i.starts_at.um)
    if not ordered:
        return ()
    groups: list[list[Indication]] = [[ordered[0]]]
    for indication in ordered[1:]:
        current = groups[-1]
        longest = max(m.length for m in current)
        gap = current[-1].gap_to(indication)
        if gap > longest * GROUP_GAP_MULTIPLE:
            groups.append([indication])
        else:
            current.append(indication)
    return tuple(Group(tuple(g)) for g in groups)


@dataclass(frozen=True, slots=True)
class Uw51Finding:
    """One thing UW-51 objects to."""

    rule: str
    reason: str
    indications: tuple[Indication, ...]

    def describe(self) -> str:
        return f"UW-51({self.rule}): {self.reason}"


def evaluate(indications: tuple[Indication, ...], wall: Length) -> tuple[Uw51Finding, ...]:
    """Every UW-51 objection to a set of indications at that wall."""
    out: list[Uw51Finding] = []
    for indication in indications:
        if indication.kind.is_always_rejectable:
            out.append(
                Uw51Finding(
                    rule="b1",
                    reason="a crack is not acceptable at any length",
                    indications=(indication,),
                )
            )
    planar = tuple(i for i in indications if i.kind.is_planar)
    limit = elongated_limit(wall)
    for indication in planar:
        if indication.kind.is_always_rejectable:
            continue
        if indication.length > limit:
            out.append(
                Uw51Finding(
                    rule="b2",
                    reason=(
                        f"{indication.length.format_mm(1)} is over the "
                        f"{limit.format_mm(1)} allowed at {wall.format_mm(1)} wall"
                    ),
                    indications=(indication,),
                )
            )
    window = window_for(wall)
    for group in group_aligned(planar):
        if len(group) < 2:
            continue
        if group.span <= window and group.aggregate > wall:
            out.append(
                Uw51Finding(
                    rule="b3",
                    reason=(
                        f"{len(group)} aligned indications total "
                        f"{group.aggregate.format_mm(1)} in {window.format_mm(1)}, "
                        f"over the {wall.format_mm(1)} allowed"
                    ),
                    indications=group.members,
                )
            )
    return tuple(out)


def sliding_limit_is_continuous() -> bool:
    """The three part limit joins up at both ends. Kept as an assertion in code."""
    at_thin = elongated_limit(THIN_LIMIT)
    at_thick = elongated_limit(THICK_LIMIT)
    just_over_thin = elongated_limit(Length(THIN_LIMIT.um + 1))
    just_under_thick = elongated_limit(Length(THICK_LIMIT.um - 1))
    return abs(at_thin.um - just_over_thin.um) <= 1 and abs(at_thick.um - just_under_thick.um) <= 1


def fraction_of_wall(indication: Indication, wall: Length) -> Decimal:
    if wall.um <= 0:
        raise ValidationError("a wall thickness has to be positive")
    return indication.length.ratio(wall)
