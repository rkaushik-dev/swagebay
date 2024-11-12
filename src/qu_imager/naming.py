"""Reading a stored or typed name back into the thing it names.

Six copies of this had grown before anybody counted them: two for the
acceptance code, two for the shot type, one for the indicator side and one for
the technique class, spread across the api, the cli, a service and the report
builder. Each was four lines and each was slightly different. One of them
accepted the enum member name as well as the value, which meant a shot type
that worked on the command line was refused by the api, and that difference had
never been written down anywhere.

So they live here, they all behave the same way, and they all accept both the
published value and the member name. That last part is the behaviour that was
right, and it is now right everywhere rather than in one place by accident.

Every one refuses rather than defaulting. A name nobody publishes is a typo or
a stale integration, and quietly picking the first entry is how a job ends up
worked to a code nobody chose.
"""

from __future__ import annotations

from enum import Enum
from typing import TypeVar

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry.shot import ShotType
from qu_imager.imaging.selection import IndicatorSide, TechniqueClass

E = TypeVar("E", bound=Enum)


def named(kind: type[E], value: str, what: str) -> E:
    """The member whose value or name matches, or a refusal naming both."""
    wanted = value.strip()
    for member in kind:
        if str(member.value) == wanted or member.name.lower() == wanted.lower():
            return member
    raise ValidationError(
        f"no {what} by that name",
        given=value,
        known=", ".join(str(m.value) for m in kind),
    )


def code_named(value: str) -> Code:
    return named(Code, value, "acceptance code")


def shot_named(value: str) -> ShotType:
    return named(ShotType, value, "shot type")


def optional_shot_named(value: str | None) -> ShotType | None:
    """Nothing means let the planner pick, which is a real answer."""
    return None if value is None else shot_named(value)


def side_named(value: str) -> IndicatorSide:
    return named(IndicatorSide, value, "indicator side")


def technique_class_named(value: str) -> TechniqueClass:
    return named(TechniqueClass, value, "technique class")


def kind_named(value: str) -> Kind:
    """A discontinuity, which is named by its code rather than its description."""
    wanted = value.strip().upper()
    for member in Kind:
        if member.name == wanted:
            return member
    raise ValidationError(
        "no discontinuity code by that name",
        given=value,
        known=", ".join(m.name for m in Kind),
    )
