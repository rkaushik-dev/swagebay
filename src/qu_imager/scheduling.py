"""When a source is allowed out of the pig.

Gamma radiography is antisocial work. The area has to be cleared of everybody
who is not on the crew, which on a running plant means nights, weekends and
shutdowns. A client gives the contractor a set of windows and the windows are
the binding constraint on the whole job, more than the source strength and more
than the crew size.

A window is a span on a particular day with a reason attached. Reasons matter
because they fail differently. A night window closes at first light and there
is nothing to negotiate. A shutdown window is open all day and can be extended
by asking. A permit window is open only while a written permit is live, and the
permit can be pulled with no notice at all.

Everything here works in local naive time on purpose. Site permits are written
in local wall clock time and a crew reading a permit that says 22:00 does not
want to think about offsets. Conversion happens at the edges, in the api layer,
where the caller knows what zone they meant.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import Duration


class WindowKind(Enum):
    NIGHT = "night"
    WEEKEND = "weekend"
    SHUTDOWN = "shutdown"
    PERMIT = "permit"

    @property
    def is_extendable(self) -> bool:
        return self in {WindowKind.SHUTDOWN, WindowKind.WEEKEND}

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, order=True, slots=True)
class Window:
    """A span during which the area can be cleared and a shot can be made."""

    starts_at: datetime
    ends_at: datetime
    kind: WindowKind = WindowKind.NIGHT

    def __post_init__(self) -> None:
        if self.starts_at.tzinfo is not None or self.ends_at.tzinfo is not None:
            raise ValidationError("site windows are local wall clock time")
        if self.ends_at <= self.starts_at:
            raise ValidationError(
                "a window has to end after it starts",
                starts_at=self.starts_at.isoformat(),
                ends_at=self.ends_at.isoformat(),
            )

    @property
    def length(self) -> Duration:
        return Duration(int((self.ends_at - self.starts_at).total_seconds() * 1000))

    def contains(self, moment: datetime) -> bool:
        return self.starts_at <= moment < self.ends_at

    def overlaps(self, other: Window) -> bool:
        return self.starts_at < other.ends_at and other.starts_at < self.ends_at

    def remaining_at(self, moment: datetime) -> Duration:
        if moment >= self.ends_at:
            return Duration.ZERO
        start = max(moment, self.starts_at)
        return Duration(int((self.ends_at - start).total_seconds() * 1000))

    def fits(self, needed: Duration, starting_at: datetime) -> bool:
        return self.remaining_at(starting_at).ms >= needed.ms

    def __str__(self) -> str:
        return f"{self.kind} {self.starts_at:%Y-%m-%d %H:%M} to {self.ends_at:%H:%M}"


@dataclass(slots=True)
class SiteSchedule:
    """The windows a client has granted, kept in order and non overlapping."""

    windows: list[Window]

    def __post_init__(self) -> None:
        self.windows = sorted(self.windows)
        for earlier, later in zip(self.windows, self.windows[1:], strict=False):
            if earlier.overlaps(later):
                raise ValidationError(
                    "two granted windows overlap",
                    first=str(earlier),
                    second=str(later),
                )

    def window_at(self, moment: datetime) -> Window | None:
        for window in self.windows:
            if window.contains(moment):
                return window
        return None

    def is_open_at(self, moment: datetime) -> bool:
        return self.window_at(moment) is not None

    def next_window_after(self, moment: datetime) -> Window | None:
        for window in self.windows:
            if window.starts_at > moment:
                return window
        return None

    def first_window_fitting(self, needed: Duration, after: datetime) -> Window | None:
        """The first window with room for a job of that length."""
        for window in self.windows:
            if window.ends_at <= after:
                continue
            if window.fits(needed, max(after, window.starts_at)):
                return window
        return None

    def total_open(self, day: date) -> Duration:
        """How much clear time there is on one calendar day."""
        start = datetime.combine(day, time.min)
        end = start + timedelta(days=1)
        total = 0
        for window in self.windows:
            overlap_start = max(window.starts_at, start)
            overlap_end = min(window.ends_at, end)
            if overlap_end > overlap_start:
                total += int((overlap_end - overlap_start).total_seconds() * 1000)
        return Duration(total)


def night_window(day: date, opens: time = time(22, 0), closes: time = time(5, 30)) -> Window:
    """The usual arrangement: opens on the given evening, closes next morning."""
    starts = datetime.combine(day, opens)
    ends = datetime.combine(day + timedelta(days=1), closes)
    return Window(starts, ends, WindowKind.NIGHT)
