"""Timestamps, and the one rule this package keeps about them.

Three kinds of moment turn up here and they are genuinely different.

A source certificate carries an instant. It is a physical fact about when the
activity was measured and it belongs in universal time, because a source
shipped from Ottawa and used in Newfoundland decayed by the same amount either
way.

A site permit carries wall clock time. It says the area is clear from twenty
two hundred, and twenty two hundred means whatever the clock on the wall of the
site office says. Converting that to universal time and back is how a window
moves an hour twice a year.

A calendar date carries neither. A weld was made on the fourth and it was made
on the fourth everywhere.

So the package stores each as what it is: aware datetimes for instants, naive
datetimes for wall clock, dates for days. What it will not do is compare across
the kinds, and the guard here is what makes that a refusal rather than a wrong
answer.
"""

from __future__ import annotations

from datetime import UTC, date, datetime, timedelta, timezone

from qu_imager.errors import ValidationError


def is_aware(moment: datetime) -> bool:
    return moment.tzinfo is not None and moment.utcoffset() is not None


def require_aware(moment: datetime, what: str = "timestamp") -> datetime:
    """An instant has to know where on earth it happened."""
    if not is_aware(moment):
        raise ValidationError(f"a {what} has to carry a time zone", given=moment.isoformat())
    return moment


def require_naive(moment: datetime, what: str = "wall clock time") -> datetime:
    """Wall clock time has to not, because the wall does not know either."""
    if is_aware(moment):
        raise ValidationError(
            f"a {what} is read off a clock and carries no zone", given=moment.isoformat()
        )
    return moment


def same_kind(left: datetime, right: datetime) -> None:
    """Refuse a comparison between an instant and a wall clock time."""
    if is_aware(left) != is_aware(right):
        raise ValidationError(
            "cannot compare an instant with a wall clock time",
            left_aware=is_aware(left),
            right_aware=is_aware(right),
        )


def to_utc(moment: datetime) -> datetime:
    """Move an instant into universal time, refusing one that is not an instant."""
    return require_aware(moment).astimezone(UTC)


def at_offset(moment: datetime, hours: int) -> datetime:
    """Read an instant at a stated offset, which is what a site report quotes."""
    if not -14 <= hours <= 14:
        raise ValidationError("that is not an offset any place uses", hours=hours)
    return require_aware(moment).astimezone(timezone(timedelta(hours=hours)))


def span(earlier: datetime, later: datetime) -> timedelta:
    """The time between two moments of the same kind."""
    same_kind(earlier, later)
    if later < earlier:
        raise ValidationError(
            "a span runs forwards", earlier=earlier.isoformat(), later=later.isoformat()
        )
    return later - earlier


def on_day(moment: datetime) -> date:
    """The calendar day a wall clock moment falls on."""
    return moment.date()


def utc_day(moment: datetime) -> date:
    """The calendar day an instant falls on in universal time.

    This is not the same as the local day and the difference is a real one: a
    shot made at ten past midnight local on the fifth was made on the fourth in
    universal time, and a source history that mixes the two has a source
    arriving before it shipped.
    """
    return to_utc(moment).date()


def start_of(day: date) -> datetime:
    """Midnight at the start of a day, as wall clock time."""
    return datetime.combine(day, datetime.min.time())


def end_of(day: date) -> datetime:
    return datetime.combine(day, datetime.max.time())


def within(moment: datetime, day: date) -> bool:
    """Whether a wall clock moment falls on a day."""
    require_naive(moment)
    return start_of(day) <= moment <= end_of(day)
