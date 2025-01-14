"""Welder qualification, which is the other credential a radiograph depends on.

A radiograph of a weld made by an unqualified welder is a radiograph of a weld
that has to come out whatever it looks like, so the inspection desk keeps the
qualification records even though it does not issue them.

Three things make a qualification and all three are ranges rather than points.
A welder qualifies by making one test weld in one position on one thickness,
and that single test buys a range of positions, a range of thicknesses and a
group of materials. The ranges are not symmetric: welding a thin test coupon
qualifies for thin work only, while welding a thick one qualifies for
everything from a floor up to twice the coupon.

Continuity is the part that catches people. A qualification lapses if the
welder has not used that process for six months, and it lapses silently. There
is no certificate that expires. The only way to know is to look at when they
last welded with it, which is why that date is on the record and why the
overdue query exists.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.quantities.length import Length

CONTINUITY_DAYS = 180
"""Six months without using a process and the qualification lapses."""

THIN_COUPON = Length.from_mm(10)
"""At or under this the coupon qualifies only up to twice its own thickness."""


class Position(Enum):
    """The welding positions, in the order they get harder."""

    FLAT = "1G"
    HORIZONTAL = "2G"
    VERTICAL = "5G"
    ALL = "6G"

    @property
    def qualifies(self) -> frozenset[Position]:
        """What making a test weld in this position buys."""
        return _POSITION_RANGE[self]

    def __str__(self) -> str:
        return self.value


_POSITION_RANGE: dict[Position, frozenset[Position]] = {
    Position.FLAT: frozenset({Position.FLAT}),
    Position.HORIZONTAL: frozenset({Position.FLAT, Position.HORIZONTAL}),
    Position.VERTICAL: frozenset({Position.FLAT, Position.HORIZONTAL, Position.VERTICAL}),
    Position.ALL: frozenset(Position),
}


class Process(Enum):
    SMAW = "shielded metal arc"
    GTAW = "gas tungsten arc"
    GMAW = "gas metal arc"
    FCAW = "flux cored arc"

    def __str__(self) -> str:
        return self.value


def thickness_range(coupon: Length) -> tuple[Length, Length]:
    """What a coupon of that thickness qualifies, which is not symmetric."""
    if coupon.um <= 0:
        raise ValidationError("a coupon has a thickness")
    if coupon <= THIN_COUPON:
        return (Length.ZERO, coupon * 2)
    return (Length.from_mm(5), coupon * 2)


@dataclass(frozen=True, slots=True)
class Qualification:
    """One welder, one process, one test weld and the ranges it bought."""

    welder: str
    process: Process
    position: Position
    coupon: Length
    qualified_on: date
    material_group: str

    def __post_init__(self) -> None:
        if not self.welder.strip():
            raise ValidationError("a qualification names a welder")
        if self.coupon.um <= 0:
            raise ValidationError("a coupon has a thickness", welder=self.welder)

    @property
    def positions(self) -> frozenset[Position]:
        return self.position.qualifies

    @property
    def thicknesses(self) -> tuple[Length, Length]:
        return thickness_range(self.coupon)

    def covers(self, position: Position, wall: Length, material_group: str) -> bool:
        low, high = self.thicknesses
        return (
            position in self.positions
            and low <= wall <= high
            and material_group == self.material_group
        )

    def describe(self) -> str:
        low, high = self.thicknesses
        return (
            f"{self.welder} {self.process} {self.position}, "
            f"{low.format_mm(1)} to {high.format_mm(1)}, group {self.material_group}"
        )


@dataclass(slots=True)
class Register:
    """Qualifications and the last time each process was used."""

    qualifications: list[Qualification] = field(default_factory=list)
    last_used: dict[tuple[str, Process], date] = field(default_factory=dict)

    def add(self, qualification: Qualification) -> None:
        key = (qualification.welder, qualification.process)
        if any(
            (q.welder, q.process) == key and q.qualified_on == qualification.qualified_on
            for q in self.qualifications
        ):
            raise ConflictError(
                "that qualification is already recorded", welder=qualification.welder
            )
        self.qualifications.append(qualification)
        self.last_used.setdefault(key, qualification.qualified_on)

    def record_use(self, welder: str, process: Process, on: date) -> None:
        key = (welder, process)
        if key not in self.last_used:
            raise ConflictError("that welder is not qualified for that process", welder=welder)
        if on > self.last_used[key]:
            self.last_used[key] = on

    def for_welder(self, welder: str) -> tuple[Qualification, ...]:
        return tuple(q for q in self.qualifications if q.welder == welder)

    def is_current_on(self, welder: str, process: Process, day: date) -> bool:
        used = self.last_used.get((welder, process))
        if used is None:
            return False
        return (day - used).days < CONTINUITY_DAYS

    def may_weld(
        self, welder: str, process: Process, position: Position, wall: Length, group: str, day: date
    ) -> bool:
        if not self.is_current_on(welder, process, day):
            return False
        return any(
            q.process is process and q.covers(position, wall, group)
            for q in self.for_welder(welder)
        )

    def lapsed_on(self, day: date) -> tuple[tuple[str, Process], ...]:
        """Every welder and process combination that has gone quiet."""
        return tuple(
            key
            for key in sorted(self.last_used, key=lambda k: (k[0], k[1].name))
            if not self.is_current_on(key[0], key[1], day)
        )

    def lapses_on(self, welder: str, process: Process) -> date | None:
        used = self.last_used.get((welder, process))
        return None if used is None else used + timedelta(days=CONTINUITY_DAYS)
