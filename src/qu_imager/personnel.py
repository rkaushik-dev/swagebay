"""Who is allowed to do what, and until when.

Two separate systems of authority land on the same person and people mix them
up constantly. Certification under the written practice says what somebody may
do with a radiograph: a Level I sets up and processes, a Level II interprets and
writes techniques, a Level III owns the procedure. Certification as a
radiographer under the radiation licence says what somebody may do with a
source: a radiographer may run a job, an assistant may only work under the
direct personal supervision of one.

A person can hold one and not the other and both combinations really happen. A
new Level II who has not sat the licence exam cannot take a source out of the
truck. A veteran radiographer whose annual eye exam lapsed last month cannot
interpret film, and the film he shoots this morning has to be read by somebody
else.

Expiry is three dates, not one, and any of them can be the one that bites: the
certification itself, the annual near vision test, and the annual colour
perception test. This module reports all three rather than the first.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ValidationError

VISION_INTERVAL_DAYS = 365
CERTIFICATION_INTERVAL_DAYS = 1095
"""Three years, which is what the written practice says for radiography."""


class Level(Enum):
    LEVEL_I = 1
    LEVEL_II = 2
    LEVEL_III = 3

    @property
    def may_interpret(self) -> bool:
        return self.value >= 2

    @property
    def may_write_technique(self) -> bool:
        return self.value >= 2

    @property
    def may_approve_procedure(self) -> bool:
        return self.value >= 3

    def __str__(self) -> str:
        return {1: "Level I", 2: "Level II", 3: "Level III"}[self.value]


class LicenceRole(Enum):
    """What the radiation licence lets somebody do with a source."""

    NONE = "not licensed"
    ASSISTANT = "radiographer assistant"
    RADIOGRAPHER = "radiographer"
    SAFETY_OFFICER = "radiation safety officer"

    @property
    def may_operate_unattended(self) -> bool:
        return self in {LicenceRole.RADIOGRAPHER, LicenceRole.SAFETY_OFFICER}

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Person:
    """Somebody on the crew, with both kinds of authority on one card."""

    badge: str
    name: str
    level: Level
    licence_role: LicenceRole
    certified_on: date
    near_vision_on: date
    colour_perception_on: date
    methods: frozenset[str] = field(default_factory=lambda: frozenset({"RT"}))

    def __post_init__(self) -> None:
        if not self.badge.strip():
            raise ValidationError("a person needs a badge number")
        if "RT" not in self.methods:
            raise ValidationError("this package only tracks radiography", badge=self.badge)

    @property
    def certification_expires(self) -> date:
        return self.certified_on + timedelta(days=CERTIFICATION_INTERVAL_DAYS)

    @property
    def near_vision_expires(self) -> date:
        return self.near_vision_on + timedelta(days=VISION_INTERVAL_DAYS)

    @property
    def colour_perception_expires(self) -> date:
        return self.colour_perception_on + timedelta(days=VISION_INTERVAL_DAYS)

    def lapsed_on(self, day: date) -> tuple[str, ...]:
        """Every credential that has run out by that day."""
        out: list[str] = []
        if day >= self.certification_expires:
            out.append("certification")
        if day >= self.near_vision_expires:
            out.append("near vision test")
        if day >= self.colour_perception_expires:
            out.append("colour perception test")
        return tuple(out)

    def is_current_on(self, day: date) -> bool:
        return not self.lapsed_on(day)

    def may_interpret_on(self, day: date) -> bool:
        return self.level.may_interpret and self.is_current_on(day)

    def may_write_technique_on(self, day: date) -> bool:
        return self.level.may_write_technique and self.is_current_on(day)

    def may_approve_procedure_on(self, day: date) -> bool:
        return self.level.may_approve_procedure and self.is_current_on(day)

    def may_shoot_alone_on(self, day: date) -> bool:
        """The licence question, which does not care about the level at all."""
        if not self.licence_role.may_operate_unattended:
            return False
        return "certification" not in self.lapsed_on(day)

    def __str__(self) -> str:
        return f"{self.name} ({self.badge}, {self.level})"


def supervision_satisfied(crew: tuple[Person, ...], day: date) -> bool:
    """An assistant on site needs a licensed radiographer on site with them."""
    if not crew:
        return False
    licensed = [p for p in crew if p.may_shoot_alone_on(day)]
    return bool(licensed)


def interpreters(crew: tuple[Person, ...], day: date) -> tuple[Person, ...]:
    return tuple(p for p in crew if p.may_interpret_on(day))
