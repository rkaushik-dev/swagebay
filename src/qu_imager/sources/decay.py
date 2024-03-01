"""Decaying a source to the day of the shot.

A source is bought with a certificate that says how many curies it held at an
assay instant, and it has been getting weaker ever since. Every exposure time
this package computes is wrong unless the activity used is the activity on the
morning of the shot, not the activity on the certificate.

Iridium loses a third of itself in five weeks. A crew that uses last month's
number underexposes by that much and reshoots the weld, which costs more than
the software did.

The arithmetic is the ordinary exponential, done in decimal rather than binary
floating point. The reason is not precision, it is reproducibility: the same
certificate and the same date have to give the same seconds today and in four
years when somebody audits the film.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal, localcontext

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.duration import MS_PER_HOUR, Duration
from qu_imager.sources.isotope import Isotope

_PRECISION = 28


def decay_fraction(isotope: Isotope, elapsed: Duration) -> Decimal:
    """What fraction of the original activity is left after that long."""
    half_life = isotope.half_life
    if half_life.ms <= 0:
        raise ValidationError("an isotope needs a positive half life", isotope=isotope.key)
    with localcontext() as ctx:
        ctx.prec = _PRECISION
        periods = Decimal(elapsed.ms) / Decimal(half_life.ms)
        return (-periods * Decimal(2).ln()).exp()


def decay(activity: Activity, isotope: Isotope, elapsed: Duration) -> Activity:
    """The activity left after that long."""
    return activity * decay_fraction(isotope, elapsed)


def activity_on(
    assay_activity: Activity,
    assay_at: datetime,
    isotope: Isotope,
    at: datetime,
) -> Activity:
    """The activity on the day of the shot, from the certificate."""
    _same_awareness(assay_at, at)
    if at < assay_at:
        raise ValidationError(
            "cannot decay a source to a date before its assay",
            assay_at=assay_at.isoformat(),
            at=at.isoformat(),
        )
    return decay(assay_activity, isotope, _span(at - assay_at))


def elapsed_for_fraction(isotope: Isotope, fraction: Decimal) -> Duration:
    """How long it takes to fall to that fraction of where it started."""
    if fraction <= 0 or fraction > 1:
        raise ValidationError("a decay fraction sits in (0, 1]", fraction=str(fraction))
    with localcontext() as ctx:
        ctx.prec = _PRECISION
        periods = -fraction.ln() / Decimal(2).ln()
        return Duration(int(periods * isotope.half_life.ms))


def time_to_reach(isotope: Isotope, start: Activity, target: Activity) -> Duration:
    """How long from that activity down to that one."""
    if start.bq == 0:
        raise ValidationError("a spent source does not decay to anything")
    if target.bq > start.bq:
        raise ValidationError("a source does not grow", start_bq=start.bq, target_bq=target.bq)
    if target.bq == 0:
        raise ValidationError("a source never reaches zero")
    return elapsed_for_fraction(isotope, target.ratio(start))


def date_reaching(isotope: Isotope, start: Activity, target: Activity, at: datetime) -> datetime:
    """The calendar date the source falls to that activity."""
    span = time_to_reach(isotope, start, target)
    return at + timedelta(milliseconds=span.ms)


def half_lives_elapsed(isotope: Isotope, elapsed: Duration) -> Decimal:
    """Useful on a source history sheet, where the number is quoted this way."""
    return Decimal(elapsed.ms) / Decimal(isotope.half_life.ms)


def _span(delta: timedelta) -> Duration:
    total_ms = int(delta.total_seconds() * 1000)
    if total_ms < 0:
        raise ValidationError("a decay span cannot be negative", ms=total_ms)
    return Duration(total_ms)


def _same_awareness(left: datetime, right: datetime) -> None:
    left_aware = left.tzinfo is not None and left.utcoffset() is not None
    right_aware = right.tzinfo is not None and right.utcoffset() is not None
    if left_aware != right_aware:
        raise ValidationError(
            "cannot compare an aware timestamp with a naive one",
            assay_aware=left_aware,
            at_aware=right_aware,
        )


def hours(value: Decimal | int) -> Duration:
    """Small helper so callers do not have to reach for milliseconds."""
    return Duration(int(Decimal(str(value)) * MS_PER_HOUR))
