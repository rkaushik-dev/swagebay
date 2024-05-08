"""Computed radiography: the plate, the scanner, and the clock.

A phosphor plate is not film. It holds a latent image in trapped electrons and
the traps leak, so the image starts fading the moment the exposure stops. A
plate scanned twenty minutes after the shot and the same plate scanned the next
morning are not the same measurement, and a crew that shoots a night's work and
scans it after breakfast has thrown away signal it paid for in exposure time.

The fade is roughly exponential and the constant is a property of the plate.
Nothing here pretends the correction recovers the lost information. It reports
the fraction remaining so a technique can be planned with enough margin, and it
flags a scan taken too late to be defensible at all.

Plate classes work the way film classes do. A finer plate resolves more and
needs more exposure to reach the same signal to noise, and a class that meets
the client specification on resolution can still fail it on noise, which is why
both numbers are on the characterization and both are checked.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length

SCAN_WITHOUT_CORRECTION = Duration.from_minutes(30)
"""Inside this the fade is small enough that nobody bothers correcting."""

SCAN_DEADLINE = Duration.from_hours(8)
"""Past this the plate is scrapped and the shot is repeated."""


@dataclass(frozen=True, slots=True)
class PlateSystem:
    """A phosphor plate as its characterization report describes it."""

    key: str
    name: str
    manufacturer: str
    ip_class: str
    basic_spatial_resolution: Length
    normalised_snr: int
    fade_half_life: Duration

    def __post_init__(self) -> None:
        if self.basic_spatial_resolution.um <= 0:
            raise ValidationError("a plate needs a resolution", key=self.key)
        if self.normalised_snr <= 0:
            raise ValidationError("a plate needs a signal to noise figure", key=self.key)
        if self.fade_half_life.ms <= 0:
            raise ValidationError("a plate fades", key=self.key)

    def remaining_signal(self, waited: Duration) -> Decimal:
        """The fraction of the latent image still there when it is scanned."""
        with localcontext() as ctx:
            ctx.prec = 28
            periods = Decimal(waited.ms) / Decimal(self.fade_half_life.ms)
            return (-periods * Decimal(2).ln()).exp()

    def scan_is_prompt(self, waited: Duration) -> bool:
        return waited <= SCAN_WITHOUT_CORRECTION

    def scan_is_usable(self, waited: Duration) -> bool:
        return waited <= SCAN_DEADLINE

    def meets_resolution(self, required: Length) -> bool:
        return self.basic_spatial_resolution <= required

    def meets_noise(self, required: int) -> bool:
        return self.normalised_snr >= required

    def shortfalls(self, required_resolution: Length, required_snr: int) -> tuple[str, ...]:
        out: list[str] = []
        if not self.meets_resolution(required_resolution):
            out.append(
                f"resolution {self.basic_spatial_resolution.format_mm(3)} is coarser than "
                f"{required_resolution.format_mm(3)}"
            )
        if not self.meets_noise(required_snr):
            out.append(f"normalised snr {self.normalised_snr} is under {required_snr}")
        return tuple(out)

    def __str__(self) -> str:
        return f"{self.name} ({self.ip_class})"


_PLATES: dict[str, PlateSystem] = {
    p.key: p
    for p in [
        PlateSystem(
            key="ip1_hd",
            name="HD-IP",
            manufacturer="Durr",
            ip_class="IP1",
            basic_spatial_resolution=Length.from_mm("0.040"),
            normalised_snr=130,
            fade_half_life=Duration.from_hours(20),
        ),
        PlateSystem(
            key="ip2_std",
            name="ST-VI",
            manufacturer="Durr",
            ip_class="IP2",
            basic_spatial_resolution=Length.from_mm("0.060"),
            normalised_snr=150,
            fade_half_life=Duration.from_hours(24),
        ),
        PlateSystem(
            key="ip3_gp",
            name="GP-D",
            manufacturer="Carestream",
            ip_class="IP3",
            basic_spatial_resolution=Length.from_mm("0.080"),
            normalised_snr=175,
            fade_half_life=Duration.from_hours(26),
        ),
        PlateSystem(
            key="ip5_fast",
            name="XL Blue",
            manufacturer="Carestream",
            ip_class="IP5",
            basic_spatial_resolution=Length.from_mm("0.130"),
            normalised_snr=210,
            fade_half_life=Duration.from_hours(30),
        ),
    ]
}


def plate(key: str) -> PlateSystem:
    try:
        return _PLATES[key]
    except KeyError:
        raise ConfigurationError("no such plate system", key=key) from None


def known_plates() -> tuple[str, ...]:
    return tuple(sorted(_PLATES))


def plates_meeting(required_resolution: Length, required_snr: int) -> tuple[PlateSystem, ...]:
    """Every plate in stock that clears both halves of the specification."""
    fits = [p for p in _PLATES.values() if not p.shortfalls(required_resolution, required_snr)]
    return tuple(sorted(fits, key=lambda p: p.basic_spatial_resolution, reverse=True))
