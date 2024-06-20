"""The any continuous twelve inches rule, done properly.

API 1104 does not say the total length of slag in the weld. It says the total
length in any continuous twelve inches of weld, and the difference is the whole
problem. A weld with eight separate half inch inclusions spread evenly over
three feet passes. The same eight bunched into one foot fails. Adding the
lengths up and comparing once gets the first case right and the second wrong.

So the window has to be slid, and slid to the worst position rather than to
convenient positions. The worst window always starts either where some
indication starts or ends exactly where some indication ends, because moving a
window that touches neither can only be moved until it does without the total
falling. That gives a finite candidate list and an exact answer, which beats
stepping the window along in millimetres and hoping.

Indications that straddle the edge of the window are clipped to the part inside
it. That is the stricter reading of two defensible ones and it is what the
written practice says to do, so a client cannot be surprised by it.

A weld shorter than the window is not measured this way at all. The code falls
back to a percentage of the weld length, because a twelve inch window on a nine
inch weld would let a nine inch defect through.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.evaluation.indication import Indication
from qu_imager.quantities.length import Length

STANDARD_WINDOW = Length.from_inch(12)
"""Twelve inches, which is what every aggregate rule in the code is written on."""


@dataclass(frozen=True, slots=True)
class Window:
    """The worst stretch of weld, and what is in it."""

    starts_at: Length
    ends_at: Length
    aggregate: Length
    count: int

    @property
    def length(self) -> Length:
        return self.ends_at - self.starts_at

    def describe(self) -> str:
        return (
            f"{self.aggregate.format_mm(1)} in the {self.length.format_mm(0)} from "
            f"{self.starts_at.format_mm(0)}"
        )


def clipped_length(indication: Indication, start: Length, end: Length) -> Length:
    """How much of one indication lies inside a window."""
    low = max(indication.starts_at, start)
    high = min(indication.ends_at, end)
    if high <= low:
        return Length.ZERO
    return high - low


def aggregate_in(
    indications: tuple[Indication, ...], start: Length, end: Length
) -> tuple[Length, int]:
    """The total clipped length inside one window, and how many contributed."""
    if end <= start:
        raise ValidationError("a window has a positive width")
    total = Length.ZERO
    count = 0
    for indication in indications:
        inside = clipped_length(indication, start, end)
        if inside.um > 0:
            total = total + inside
            count += 1
    return total, count


def worst_window(
    indications: tuple[Indication, ...],
    weld_length: Length,
    window: Length = STANDARD_WINDOW,
) -> Window:
    """The window position with the most in it. Exact, not sampled."""
    if weld_length.um <= 0:
        raise ValidationError("a weld has a length")
    if window.um <= 0:
        raise ValidationError("a window has a positive width")
    if not indications:
        return Window(Length.ZERO, min(window, weld_length), Length.ZERO, 0)

    span = min(window, weld_length)
    candidates: set[int] = {0}
    for indication in indications:
        candidates.add(indication.starts_at.um)
        candidates.add(indication.ends_at.um - span.um)
    limit = weld_length.um - span.um

    best = Window(Length.ZERO, span, Length.ZERO, 0)
    for raw in sorted(candidates):
        start_um = min(max(raw, 0), max(limit, 0))
        start = Length(start_um)
        end = Length(start_um + span.um)
        total, count = aggregate_in(indications, start, end)
        if total > best.aggregate:
            best = Window(start, end, total, count)
    return best


def fraction_of_weld(aggregate: Length, weld_length: Length) -> Decimal:
    """What the short weld rule compares against."""
    if weld_length.um <= 0:
        raise ValidationError("a weld has a length")
    return aggregate.ratio(weld_length)


def window_applies(weld_length: Length, window: Length = STANDARD_WINDOW) -> bool:
    """Whether the weld is long enough for the window rule to be the right one."""
    return weld_length >= window


def count_in_worst_window(
    indications: tuple[Indication, ...],
    weld_length: Length,
    window: Length = STANDARD_WINDOW,
) -> int:
    """Some rules cap the number of indications rather than their total length."""
    if not indications:
        return 0
    span = min(window, weld_length)
    candidates = sorted({0, *(i.starts_at.um for i in indications)})
    limit = max(weld_length.um - span.um, 0)
    best = 0
    for raw in candidates:
        start_um = min(raw, limit)
        _total, count = aggregate_in(indications, Length(start_um), Length(start_um + span.um))
        best = max(best, count)
    return best
