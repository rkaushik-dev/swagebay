"""Sealed sources, their certificates, and whether one is fit to go out.

A sealed source is a pellet of active material welded inside two capsules and
crimped onto a pigtail. What the office holds is the certificate: a serial
number, the isotope, how many curies it held at an assay instant, and the size
of the active pellet. The last one is the number nobody expects to need and it
turns out to matter more than the curies, because the pellet size is the focal
spot and the focal spot decides how sharp the image can be at all.

Fitness to go out is three separate questions and they fail differently. Is
there enough activity left to make the shot in a reasonable time. Is the leak
test current, which is a regulatory question with a hard date. Is the source
still in service rather than in transit or waiting on disposal. A source can
pass all three and still be the wrong source for the weld, which is a planning
question and lives elsewhere.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.length import Length
from qu_imager.sources.decay import activity_on
from qu_imager.sources.isotope import Isotope

LEAK_TEST_INTERVAL_DAYS = 180
"""Six months, which is what the licence says and it is not negotiable."""


class SourceState(Enum):
    IN_SERVICE = "in service"
    IN_TRANSIT = "in transit"
    STORED = "stored"
    AWAITING_DISPOSAL = "awaiting disposal"
    DISPOSED = "disposed"

    @property
    def can_be_used(self) -> bool:
        return self in {SourceState.IN_SERVICE, SourceState.STORED}


@dataclass(frozen=True, slots=True)
class LeakTest:
    """A wipe test and what it found."""

    performed_at: datetime
    becquerels_removed: Decimal
    technician: str

    LIMIT_BQ = Decimal(185)
    """Two hundredths of a microcurie, the reportable threshold."""

    @property
    def passed(self) -> bool:
        return self.becquerels_removed < self.LIMIT_BQ

    def expires_at(self) -> datetime:
        return self.performed_at + timedelta(days=LEAK_TEST_INTERVAL_DAYS)


@dataclass(frozen=True, slots=True)
class SourceCertificate:
    """What came in the box with the source."""

    serial: str
    isotope: Isotope
    assay_activity: Activity
    assay_at: datetime
    manufacturer: str
    model: str
    active_diameter: Length
    active_length: Length

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise ValidationError("a source certificate needs a serial number")
        if self.assay_activity.bq <= 0:
            raise ValidationError("an assay activity has to be positive", serial=self.serial)
        for name, value in (
            ("active_diameter", self.active_diameter),
            ("active_length", self.active_length),
        ):
            if value.um <= 0:
                raise ValidationError(
                    "an active pellet has to have a size", serial=self.serial, field=name
                )

    @property
    def focal_size(self) -> Length:
        """The dimension that goes into the unsharpness formula.

        The pellet is a cylinder and it projects as a rectangle. Taking the
        larger of the two dimensions is the conservative reading and it is what
        the procedure says to do, because the crew does not know which way the
        pigtail landed in the guide tube.
        """
        return max(self.active_diameter, self.active_length)


@dataclass(slots=True)
class SealedSource:
    """A source the shop owns, with the history that decides if it can go out."""

    certificate: SourceCertificate
    state: SourceState = SourceState.IN_SERVICE
    working_floor: Activity = field(default_factory=lambda: Activity.from_curie(15))
    leak_tests: list[LeakTest] = field(default_factory=list)

    @property
    def serial(self) -> str:
        return self.certificate.serial

    @property
    def isotope(self) -> Isotope:
        return self.certificate.isotope

    def activity_at(self, at: datetime) -> Activity:
        return activity_on(
            self.certificate.assay_activity, self.certificate.assay_at, self.isotope, at
        )

    def latest_leak_test(self) -> LeakTest | None:
        if not self.leak_tests:
            return None
        return max(self.leak_tests, key=lambda t: t.performed_at)

    def leak_test_current_at(self, at: datetime) -> bool:
        latest = self.latest_leak_test()
        if latest is None or not latest.passed:
            return False
        return at < latest.expires_at()

    def reasons_not_to_use(self, at: datetime) -> tuple[str, ...]:
        """Everything wrong with taking this source to a job that morning."""
        reasons: list[str] = []
        if not self.state.can_be_used:
            reasons.append(f"source is {self.state.value}")
        if not self.leak_test_current_at(at):
            reasons.append("leak test is not current")
        if self.activity_at(at).is_spent(self.working_floor):
            reasons.append("activity is below the working floor")
        return tuple(reasons)

    def is_fit_at(self, at: datetime) -> bool:
        return not self.reasons_not_to_use(at)

    def record_leak_test(self, test: LeakTest) -> None:
        self.leak_tests.append(test)
        if not test.passed:
            self.state = SourceState.AWAITING_DISPOSAL

    def __str__(self) -> str:
        return f"{self.isotope} {self.serial}"
