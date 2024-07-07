"""Repairs, re-shoots, and the number the client actually watches.

A weld that fails is cut out and welded again, and then it is shot again. Three
things about that are worth encoding because all three get argued about.

A repair is a round of the same weld, not a new weld. The identifier carries
the round and the register keys on the original, so a shop cannot improve its
numbers by renaming. This is the whole reason the identifier grammar has an R
suffix in it.

The re-shoot has to cover more than the excavation. The weld metal either side
of a cut out is affected by the second thermal cycle and the code wants it
examined, so the film has to reach past the ends of the excavation by a margin.
A re-shoot that covers exactly the excavation and no more is a finding, and it
is a common one because it is what a crew does when the excavation is near the
end of a film.

A second repair of the same weld needs the client to say yes. Not because two
repairs are twice as bad as one, but because a weld that has failed twice is
telling somebody something about the procedure or the welder, and the client is
entitled to be part of that conversation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.identity import WeldId
from qu_imager.quantities.length import Length

REPAIR_MARGIN = Length.from_mm(25)
"""How far past each end of the excavation the re-shoot has to reach."""

REPAIRS_WITHOUT_APPROVAL = 1


@dataclass(frozen=True, slots=True)
class Excavation:
    """The stretch of weld that was cut out."""

    starts_at: Length
    ends_at: Length

    def __post_init__(self) -> None:
        if self.starts_at.um < 0:
            raise ValidationError("an excavation starts somewhere on the weld")
        if self.ends_at <= self.starts_at:
            raise ValidationError("an excavation has a positive length")

    @property
    def length(self) -> Length:
        return self.ends_at - self.starts_at

    def coverage_required(self) -> tuple[Length, Length]:
        """The stretch the re-shoot has to cover, margin included."""
        start = Length(max(0, self.starts_at.um - REPAIR_MARGIN.um))
        return (start, self.ends_at + REPAIR_MARGIN)


@dataclass(frozen=True, slots=True)
class Repair:
    """One round of cutting out and welding again."""

    weld: WeldId
    excavation: Excavation
    welded_by: str
    welded_on: date
    client_approved: bool = False

    def __post_init__(self) -> None:
        if not self.welded_by.strip():
            raise ValidationError("a repair is signed by the welder")
        if not self.weld.is_repair:
            raise ValidationError(
                "a repair belongs to a repair round of the weld", weld=str(self.weld)
            )

    @property
    def round(self) -> int:
        return self.weld.repair


@dataclass(frozen=True, slots=True)
class ReShoot:
    """The film taken over a repair."""

    weld: WeldId
    covers_from: Length
    covers_to: Length

    def __post_init__(self) -> None:
        if self.covers_to <= self.covers_from:
            raise ValidationError("a re-shoot covers a positive length")

    def covers(self, repair: Repair) -> bool:
        required_start, required_end = repair.excavation.coverage_required()
        return self.covers_from <= required_start and self.covers_to >= required_end

    def shortfall(self, repair: Repair) -> tuple[Length, Length]:
        """How far short the film is at each end. Zero either side when it reaches."""
        required_start, required_end = repair.excavation.coverage_required()
        before = (
            Length(self.covers_from.um - required_start.um)
            if self.covers_from > required_start
            else Length.ZERO
        )
        after = (
            Length(required_end.um - self.covers_to.um)
            if self.covers_to < required_end
            else Length.ZERO
        )
        return (before, after)


@dataclass(slots=True)
class RepairRegister:
    """Every repair on a job, keyed on the original weld."""

    repairs: list[Repair] = field(default_factory=list)

    def record(self, repair: Repair) -> None:
        rounds = self.rounds_for(repair.weld.original)
        if repair.round in rounds:
            raise ConflictError("that repair round is already recorded", weld=str(repair.weld))
        if repair.round > REPAIRS_WITHOUT_APPROVAL and not repair.client_approved:
            raise ConflictError(
                "a second repair of the same weld needs the client to approve it",
                weld=str(repair.weld),
                round=repair.round,
            )
        self.repairs.append(repair)

    def rounds_for(self, weld: WeldId) -> tuple[int, ...]:
        original = weld.original
        return tuple(sorted(r.round for r in self.repairs if r.weld.original == original))

    def repaired_welds(self) -> frozenset[WeldId]:
        return frozenset(r.weld.original for r in self.repairs)

    def rate_over(self, welds_shot: int) -> Decimal:
        """Repaired welds over welds shot, which is the number on the report."""
        if welds_shot <= 0:
            raise ValidationError("a repair rate needs a denominator", welds_shot=welds_shot)
        return Decimal(len(self.repaired_welds())) / Decimal(welds_shot)

    def repeat_offenders(self) -> tuple[WeldId, ...]:
        return tuple(
            sorted(
                (w for w in self.repaired_welds() if len(self.rounds_for(w)) > 1),
                key=str,
            )
        )
