"""What may be done to a digital radiograph, and what may not.

This is the part of digital radiography that makes people who grew up on film
uncomfortable, and they are right to be. A film is a physical object and what
is on it is what came out of the processor. A digital image is numbers, and
somebody can change the numbers.

The line the codes draw is between operations that are reversible and
operations that are not. Windowing and contrast stretch change what is on the
screen and leave the stored data alone, so they are viewing controls and
anybody may use them. Sharpening, smoothing and noise reduction throw
information away, cannot be undone, and can create an edge that was not there,
so they are restricted: allowed only if they were qualified as part of the
technique and recorded on the record.

Two operations are never allowed on a graded image at all. Local edits to part
of the image are not enhancement, they are alteration. And a second lossy
compression on top of the first destroys exactly the small low contrast detail
the radiograph exists to show.

Every applied operation is recorded in order, because the order changes the
result and a list without an order is not a record of what happened.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from qu_imager.errors import QualificationError, ValidationError


class Operation(Enum):
    """Something somebody did to the image."""

    WINDOW = "window and level"
    CONTRAST_STRETCH = "contrast stretch"
    ROTATE_90 = "rotate by a right angle"
    MIRROR = "mirror"
    SHARPEN = "unsharp mask"
    SMOOTH = "smoothing"
    NOISE_REDUCTION = "noise reduction"
    LOCAL_EDIT = "local edit"
    LOSSY_COMPRESSION = "lossy compression"

    @property
    def is_reversible(self) -> bool:
        """Whether the stored data survives it."""
        return self in {
            Operation.WINDOW,
            Operation.CONTRAST_STRETCH,
            Operation.ROTATE_90,
            Operation.MIRROR,
        }

    @property
    def needs_qualification(self) -> bool:
        return self in {
            Operation.SHARPEN,
            Operation.SMOOTH,
            Operation.NOISE_REDUCTION,
        }

    @property
    def is_forbidden(self) -> bool:
        return self in {Operation.LOCAL_EDIT, Operation.LOSSY_COMPRESSION}

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Applied:
    """One operation, applied by somebody with a setting."""

    operation: Operation
    setting: str
    applied_by: str

    def __post_init__(self) -> None:
        if not self.applied_by.strip():
            raise ValidationError("an operation is applied by somebody")

    def describe(self) -> str:
        detail = f" {self.setting}" if self.setting.strip() else ""
        return f"{self.operation}{detail} by {self.applied_by}"


@dataclass(frozen=True, slots=True)
class History:
    """Everything done to one image, in the order it was done."""

    operations: tuple[Applied, ...] = ()

    def then(self, applied: Applied) -> History:
        return History((*self.operations, applied))

    @property
    def is_reversible(self) -> bool:
        return all(item.operation.is_reversible for item in self.operations)

    def irreversible(self) -> tuple[Applied, ...]:
        return tuple(item for item in self.operations if not item.operation.is_reversible)

    def describe(self) -> str:
        if not self.operations:
            return "unprocessed"
        return " then ".join(item.describe() for item in self.operations)


def faults(history: History, qualified: frozenset[Operation]) -> tuple[str, ...]:
    """Everything about a processing history that a reviewer would object to."""
    out: list[str] = []
    for item in history.operations:
        if item.operation.is_forbidden:
            out.append(f"{item.operation} is not allowed on a graded image")
        elif item.operation.needs_qualification and item.operation not in qualified:
            out.append(f"{item.operation} was applied but is not part of the qualified technique")
    compressions = [
        item for item in history.operations if item.operation is Operation.LOSSY_COMPRESSION
    ]
    if len(compressions) > 1:
        out.append("the image has been compressed more than once")
    return tuple(out)


def is_admissible(history: History, qualified: frozenset[Operation]) -> bool:
    return not faults(history, qualified)


def check(history: History, qualified: frozenset[Operation]) -> None:
    """Refuse an image whose processing history disqualifies it."""
    found = faults(history, qualified)
    if found:
        raise QualificationError(
            "the processing history disqualifies this image", reasons="; ".join(found)
        )


def viewing_only() -> frozenset[Operation]:
    """The set a shop qualifies when it qualifies nothing, which is the usual case."""
    return frozenset(op for op in Operation if op.is_reversible)
