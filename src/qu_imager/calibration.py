"""The calibration register, which is the first thing an auditor asks for.

Every instrument the shop uses to produce a number has an interval, and the
intervals are all different: a survey meter is annual, a densitometer is
checked every shift against a step wedge, a step wedge itself is replaced
rather than calibrated, and a dosimeter is whatever the laboratory contract
says. Keeping them in one register with the interval as data rather than in
four places with the interval in the code is the difference between one
overdue list and four.

Two dates are kept for every instrument and they are not the same date. The
calibration date is when it was done. The due date is when it runs out, and it
is not always the calibration date plus the interval, because a laboratory
sometimes issues a certificate valid to a date of its own. Storing the due date
rather than deriving it means the certificate wins, which is correct.

An instrument with no calibration on record is overdue, not unknown. The
difference matters because an unknown sorts out of the way and an overdue does
not.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ConflictError, ValidationError


class Instrument(Enum):
    """What kind of thing this is, which sets the default interval."""

    SURVEY_METER = "survey meter"
    DENSITOMETER = "densitometer"
    STEP_WEDGE = "step wedge"
    DOSIMETER = "dosimeter"
    ALARM = "alarming ratemeter"
    VIEWER = "viewer"

    @property
    def default_interval(self) -> timedelta:
        return _INTERVALS[self]

    @property
    def is_replaced_rather_than_calibrated(self) -> bool:
        return self is Instrument.STEP_WEDGE

    def __str__(self) -> str:
        return self.value


_INTERVALS: dict[Instrument, timedelta] = {
    Instrument.SURVEY_METER: timedelta(days=365),
    Instrument.DENSITOMETER: timedelta(days=365),
    Instrument.STEP_WEDGE: timedelta(days=730),
    Instrument.DOSIMETER: timedelta(days=365),
    Instrument.ALARM: timedelta(days=365),
    Instrument.VIEWER: timedelta(days=730),
}


@dataclass(frozen=True, slots=True)
class Certificate:
    """One calibration, as the laboratory issued it."""

    calibrated_on: date
    due_on: date
    laboratory: str
    reference: str

    def __post_init__(self) -> None:
        if self.due_on <= self.calibrated_on:
            raise ValidationError(
                "a certificate expires after it was issued",
                calibrated_on=self.calibrated_on.isoformat(),
                due_on=self.due_on.isoformat(),
            )
        if not self.laboratory.strip():
            raise ValidationError("a certificate names the laboratory")

    def is_current_on(self, day: date) -> bool:
        return self.calibrated_on <= day < self.due_on


@dataclass(slots=True)
class Item:
    """One instrument the shop owns."""

    serial: str
    kind: Instrument
    certificates: list[Certificate] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise ValidationError("an instrument has a serial number")

    def latest(self) -> Certificate | None:
        if not self.certificates:
            return None
        return max(self.certificates, key=lambda c: c.calibrated_on)

    def due_on(self) -> date | None:
        latest = self.latest()
        return latest.due_on if latest is not None else None

    def is_current_on(self, day: date) -> bool:
        latest = self.latest()
        return latest is not None and latest.is_current_on(day)

    def days_left_on(self, day: date) -> int | None:
        due = self.due_on()
        return None if due is None else (due - day).days


@dataclass(slots=True)
class Register:
    """Every instrument, with one overdue list rather than four."""

    items: dict[str, Item] = field(default_factory=dict)

    def add(self, item: Item) -> None:
        if item.serial in self.items:
            raise ConflictError("that instrument is already registered", serial=item.serial)
        self.items[item.serial] = item

    def get(self, serial: str) -> Item:
        try:
            return self.items[serial]
        except KeyError:
            raise ConflictError("no instrument with that serial", serial=serial) from None

    def calibrate(self, serial: str, certificate: Certificate) -> None:
        self.get(serial).certificates.append(certificate)

    def overdue_on(self, day: date) -> tuple[str, ...]:
        """Every instrument out of calibration, including the ones never done."""
        return tuple(
            serial for serial, item in sorted(self.items.items()) if not item.is_current_on(day)
        )

    def due_within(self, day: date, days: int) -> tuple[str, ...]:
        """What to book in, which is the list somebody can still act on."""
        if days < 0:
            raise ValidationError("a look ahead is not negative", days=days)
        out: list[str] = []
        for serial, item in sorted(self.items.items()):
            left = item.days_left_on(day)
            if left is not None and 0 <= left <= days:
                out.append(serial)
        return tuple(out)

    def default_due(self, serial: str, calibrated_on: date) -> date:
        """The due date the interval implies, for when a certificate does not say."""
        return calibrated_on + self.get(serial).kind.default_interval
