"""One thing the reader found, measured the way the code will ask for it.

A reader looking at a film writes down what they see and where. What the code
then wants is not what the reader wrote, it is three numbers: how long it is
along the weld, how wide it is across the weld, and where it starts. Undercut
also wants how deep, expressed as a fraction of the wall, because the code
judges it that way rather than in millimetres.

Two shapes of indication behave differently and it is worth keeping the
distinction in the type. An elongated indication has a length and the length is
what the code measures. A rounded one has a diameter and no meaningful length,
and giving it a length invites somebody to add it into an aggregate that it
does not belong in.

Position is measured from the first location marker, which is the only origin
that survives the film being taken off the pipe. Clock position is carried
alongside because that is what a repair crew will actually use to find it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.identity import ClockPosition
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Indication:
    """One discontinuity, located and measured."""

    kind: Kind
    starts_at: Length
    length: Length
    width: Length = Length.ZERO
    depth_fraction: Decimal | None = None
    clock: ClockPosition | None = None
    radiograph: str = ""

    def __post_init__(self) -> None:
        if self.starts_at.um < 0:
            raise ValidationError("an indication is somewhere on the weld")
        if self.length.um < 0 or self.width.um < 0:
            raise ValidationError("an indication cannot have a negative size")
        if self.kind.is_rounded:
            if self.width.um <= 0:
                raise ValidationError(
                    "a rounded indication is measured across, not along", kind=self.kind.code
                )
        elif self.length.um <= 0:
            raise ValidationError("an elongated indication needs a length", kind=self.kind.code)
        if self.depth_fraction is not None and not 0 < self.depth_fraction <= 1:
            raise ValidationError("a depth fraction sits in (0, 1]", depth=str(self.depth_fraction))

    @property
    def ends_at(self) -> Length:
        return self.starts_at + self.length

    @property
    def size(self) -> Length:
        """The dimension the code will judge, which depends on the shape."""
        return self.width if self.kind.is_rounded else self.length

    def overlaps(self, other: Indication) -> bool:
        return self.starts_at < other.ends_at and other.starts_at < self.ends_at

    def gap_to(self, other: Indication) -> Length:
        """The clear distance between two indications along the weld."""
        if self.overlaps(other):
            return Length.ZERO
        if self.ends_at <= other.starts_at:
            return other.starts_at - self.ends_at
        return self.starts_at - other.ends_at

    def describe(self) -> str:
        where = f" at {self.clock}" if self.clock is not None else ""
        return (
            f"{self.kind.code} {self.size.format_mm(1)} from {self.starts_at.format_mm(0)}{where}"
        )


@dataclass(frozen=True, slots=True)
class IndicationSet:
    """Everything found on one weld, in order along it."""

    weld_length: Length
    indications: tuple[Indication, ...]

    def __post_init__(self) -> None:
        if self.weld_length.um <= 0:
            raise ValidationError("a weld has a length")
        for indication in self.indications:
            if indication.ends_at > self.weld_length:
                raise ValidationError(
                    "an indication runs off the end of the weld",
                    kind=indication.kind.code,
                    ends_at_mm=str(indication.ends_at.mm),
                )
        object.__setattr__(
            self, "indications", tuple(sorted(self.indications, key=lambda i: i.starts_at.um))
        )

    def of_kind(self, kind: Kind) -> tuple[Indication, ...]:
        return tuple(i for i in self.indications if i.kind is kind)

    def planar(self) -> tuple[Indication, ...]:
        return tuple(i for i in self.indications if i.kind.is_planar)

    def kinds(self) -> frozenset[Kind]:
        return frozenset(i.kind for i in self.indications)

    def total_length_of(self, kind: Kind) -> Length:
        total = Length.ZERO
        for indication in self.of_kind(kind):
            total = total + indication.length
        return total

    def within(self, start: Length, end: Length) -> tuple[Indication, ...]:
        """Everything that overlaps a window along the weld."""
        if end <= start:
            raise ValidationError("a window has a positive width")
        return tuple(i for i in self.indications if i.starts_at < end and start < i.ends_at)

    def longest_of(self, kind: Kind) -> Indication | None:
        found = self.of_kind(kind)
        return max(found, key=lambda i: i.size.um) if found else None

    def __len__(self) -> int:
        return len(self.indications)
