"""Fitting a night's work into the windows the client gave.

A shift is a list of joints and a list of windows, and the question is which
joints get shot tonight. It looks like a packing problem and it is not, because
the order is not free: a crew works its way along a rack and going back for one
joint costs more than the joint is worth.

So the plan walks the joints in the order they were given, fits each one into
the window that is open when the crew reaches it, and stops when the window
closes. Anything that did not fit rolls to the next window and then to the next
night, and the plan says which so the coordinator can see what tomorrow looks
like before tonight starts.

Setup time is counted once per window rather than once per joint. Roping off an
area, walking the boundary and posting it takes the same forty minutes whether
one joint is shot inside it or six, and a planner that charges it per joint
under books every night.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import Duration
from qu_imager.scheduling import SiteSchedule, Window

DEFAULT_SETUP = Duration.from_minutes(40)
"""Roping off, walking the boundary and posting it, once per window."""


@dataclass(frozen=True, slots=True)
class Piece:
    """One joint and how long the source is out for it."""

    weld: str
    source_out: Duration

    def __post_init__(self) -> None:
        if not self.weld.strip():
            raise ValidationError("a piece of work names a weld")
        if self.source_out.ms <= 0:
            raise ValidationError("a piece of work takes some time", weld=self.weld)


@dataclass(frozen=True, slots=True)
class Slot:
    """One joint, placed in one window."""

    weld: str
    window_starts_at: datetime
    starts_at: datetime
    ends_at: datetime

    @property
    def length(self) -> Duration:
        return Duration(int((self.ends_at - self.starts_at).total_seconds() * 1000))


@dataclass(frozen=True, slots=True)
class Plan:
    """What fits tonight and what does not."""

    slots: tuple[Slot, ...]
    left_over: tuple[str, ...]
    windows_used: int

    @property
    def is_complete(self) -> bool:
        return not self.left_over

    def describe(self) -> str:
        if self.is_complete:
            return f"{len(self.slots)} joints across {self.windows_used} windows"
        return (
            f"{len(self.slots)} joints across {self.windows_used} windows, "
            f"{len(self.left_over)} left over"
        )


def plan_shift(
    schedule: SiteSchedule,
    work: tuple[Piece, ...],
    setup: Duration = DEFAULT_SETUP,
) -> Plan:
    """Walk the joints in order and fit them into the windows in order."""
    if setup.ms < 0:
        raise ValidationError("a setup time is not negative")
    remaining = list(work)
    slots: list[Slot] = []
    used = 0

    for window in schedule.windows:
        if not remaining:
            break
        clock = window.starts_at + _delta(setup)
        if clock >= window.ends_at:
            continue
        placed_here = 0
        while remaining:
            piece = remaining[0]
            finish = clock + _delta(piece.source_out)
            if finish > window.ends_at:
                break
            slots.append(
                Slot(
                    weld=piece.weld,
                    window_starts_at=window.starts_at,
                    starts_at=clock,
                    ends_at=finish,
                )
            )
            clock = finish
            placed_here += 1
            remaining.pop(0)
        if placed_here:
            used += 1

    return Plan(
        slots=tuple(slots),
        left_over=tuple(piece.weld for piece in remaining),
        windows_used=used,
    )


def window_utilisation(plan: Plan, window: Window, setup: Duration = DEFAULT_SETUP) -> Duration:
    """How much of one window the plan actually uses, setup included."""
    inside = [s for s in plan.slots if s.window_starts_at == window.starts_at]
    if not inside:
        return Duration.ZERO
    total = sum(slot.length.ms for slot in inside)
    return Duration(total + setup.ms)


def spare_in(plan: Plan, window: Window, setup: Duration = DEFAULT_SETUP) -> Duration:
    used = window_utilisation(plan, window, setup)
    if used.ms >= window.length.ms:
        return Duration.ZERO
    return Duration(window.length.ms - used.ms)


def _delta(span: Duration):  # noqa: ANN202
    from datetime import timedelta

    return timedelta(milliseconds=span.ms)
