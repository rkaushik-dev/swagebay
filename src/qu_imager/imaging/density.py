"""Reading density off a film, and whether the readings pass.

A densitometer is a lamp, a hole and a photocell, and it drifts. So a reading
is not a number, it is a number plus the calibration behind it, and a reading
taken on an instrument whose step wedge check was yesterday is worth less than
one taken after this morning's check. The record has to carry both or the whole
density argument is unfalsifiable.

The gate has two halves that fail for different reasons. The absolute band says
the film is neither too thin to have contrast nor too dense to get light
through. The variation rule says the density under the weld has not wandered
away from the density under the indicator, which catches the shot where the
part was wedge shaped or the beam was off axis, and which a single reading in
the middle of the film cannot catch at all.

Three readings is the practical minimum for a weld of any length: one at the
indicator and one either side of it. Fewer than that and the variation rule has
nothing to compare.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.imaging.film import (
    DENSITY_CEILING,
    density_floor,
    variation_acceptable,
    variation_window,
)

CALIBRATION_INTERVAL = timedelta(hours=8)
"""A shift. The step wedge check happens at the start of one."""

STEP_WEDGE_TOLERANCE = Decimal("0.05")


@dataclass(frozen=True, slots=True)
class Calibration:
    """A step wedge check on the densitometer."""

    checked_at: datetime
    step_nominal: Decimal
    step_read: Decimal
    instrument: str

    @property
    def error(self) -> Decimal:
        return self.step_read - self.step_nominal

    @property
    def passed(self) -> bool:
        return abs(self.error) <= STEP_WEDGE_TOLERANCE

    def is_current_at(self, moment: datetime) -> bool:
        if not self.passed:
            return False
        return self.checked_at <= moment < self.checked_at + CALIBRATION_INTERVAL


@dataclass(frozen=True, slots=True)
class Reading:
    """One spot on the film, with what was under it."""

    label: str
    density: Decimal
    through_indicator: bool = False

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("a density reading needs a location label")
        if self.density <= 0:
            raise ValidationError("a density reading has to be positive", label=self.label)
        if self.density > DENSITY_CEILING * 2:
            raise ValidationError("that reading is off the instrument scale", label=self.label)


@dataclass(frozen=True, slots=True)
class DensityVerdict:
    """Whether the film passes on density, and every reason it does not."""

    passed: bool
    reasons: tuple[str, ...]
    indicator_density: Decimal | None
    low: Decimal | None
    high: Decimal | None

    def describe(self) -> str:
        if self.passed:
            return "density acceptable"
        return "; ".join(self.reasons)


def grade(
    readings: tuple[Reading, ...],
    gamma: bool,
    composite: bool = False,
    calibration: Calibration | None = None,
    read_at: datetime | None = None,
) -> DensityVerdict:
    """Apply the absolute band and the variation rule to a set of readings."""
    if not readings:
        raise ValidationError("there is nothing to grade")

    reasons: list[str] = []
    if calibration is None:
        reasons.append("no densitometer calibration on record")
    elif read_at is not None and not calibration.is_current_at(read_at):
        reasons.append("densitometer calibration is not current")

    floor = density_floor(gamma, composite)
    for reading in readings:
        if reading.density < floor:
            reasons.append(f"{reading.label} reads {reading.density} below the floor of {floor}")
        elif reading.density > DENSITY_CEILING:
            reasons.append(
                f"{reading.label} reads {reading.density} above the ceiling of {DENSITY_CEILING}"
            )

    through = [r for r in readings if r.through_indicator]
    indicator_density: Decimal | None = None
    low: Decimal | None = None
    high: Decimal | None = None
    if len(through) != 1:
        reasons.append("exactly one reading has to be taken through the indicator")
    else:
        indicator_density = through[0].density
        low, high = variation_window(indicator_density)
        for reading in readings:
            if reading.through_indicator:
                continue
            if not variation_acceptable(indicator_density, reading.density):
                reasons.append(
                    f"{reading.label} reads {reading.density} outside the variation window"
                )

    return DensityVerdict(
        passed=not reasons,
        reasons=tuple(reasons),
        indicator_density=indicator_density,
        low=low,
        high=high,
    )
