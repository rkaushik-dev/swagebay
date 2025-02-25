"""The plate scanner, whose settings are part of the technique either way.

A phosphor plate holds an image. The scanner is what turns it into numbers, and
three settings on it change the result as much as the exposure did.

Scan pitch is the step the laser takes. It sets the pixel size and therefore
the ceiling on resolution: a fifty micrometre pitch cannot resolve anything a
plate could have held at forty. Scanning finer than the plate can hold is
wasted time and a bigger file.

Gain is the photomultiplier voltage. High gain reads a thin plate at the cost
of noise, low gain needs more exposure and gives a cleaner image. It is the
same trade as film speed and it is made after the shot rather than before,
which is what makes it dangerous: a crew that underexposes and turns the gain
up gets an image that passes on density and fails on signal to noise.

Latitude is the range of exposure the scan covers. Too narrow and part of the
image clips.

None of this is on the technique sheet in most shops, which is why the same
plate scanned twice on different settings produces two different verdicts and
nobody can say which was right.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.imaging.computed import PlateSystem
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Settings:
    """The three knobs, as they were set for one scan."""

    pitch: Length
    gain: int
    latitude: int

    def __post_init__(self) -> None:
        if self.pitch.um <= 0:
            raise ValidationError("a scan has a pitch")
        if not 1 <= self.gain <= 10:
            raise ValidationError("gain runs from one to ten", gain=self.gain)
        if not 1 <= self.latitude <= 5:
            raise ValidationError("latitude runs from one to five", latitude=self.latitude)

    @property
    def noise_penalty(self) -> Decimal:
        """How much the gain setting costs in signal to noise, as a factor."""
        with localcontext() as ctx:
            ctx.prec = 28
            return (Decimal(2).ln() * Decimal(self.gain - 1) / 4).exp()

    def describe(self) -> str:
        return f"{self.pitch.format_mm(3)} pitch, gain {self.gain}, latitude {self.latitude}"


def resolution_ceiling(settings: Settings) -> Length:
    """The finest thing a scan at that pitch can resolve, which is two pixels."""
    return settings.pitch * 2


def achievable_resolution(plate: PlateSystem, settings: Settings) -> Length:
    """Whichever of the plate and the scan is the coarser."""
    return max(plate.basic_spatial_resolution, resolution_ceiling(settings))


def is_oversampled(plate: PlateSystem, settings: Settings) -> bool:
    """Scanning finer than the plate can hold is time and disk for nothing."""
    return resolution_ceiling(settings) < plate.basic_spatial_resolution


def effective_snr(plate: PlateSystem, settings: Settings) -> Decimal:
    """The plate figure after the gain has been paid for."""
    return Decimal(plate.normalised_snr) / settings.noise_penalty


def faults(
    plate: PlateSystem,
    settings: Settings,
    required_resolution: Length,
    required_snr: int,
) -> tuple[str, ...]:
    """Everything wrong with reading that plate on those settings."""
    out: list[str] = []
    achieved = achievable_resolution(plate, settings)
    if achieved > required_resolution:
        out.append(
            f"resolution {achieved.format_mm(3)} is coarser than {required_resolution.format_mm(3)}"
        )
    snr = effective_snr(plate, settings)
    if snr < required_snr:
        out.append(f"signal to noise {snr:.0f} is under {required_snr} at gain {settings.gain}")
    if is_oversampled(plate, settings):
        out.append(
            f"a {settings.pitch.format_mm(3)} pitch is finer than the plate holds, "
            "which costs time and gains nothing"
        )
    return tuple(out)


def recommended_pitch(plate: PlateSystem) -> Length:
    """Half the plate resolution, which is as fine as it is worth scanning."""
    return plate.basic_spatial_resolution / 2


def lowest_workable_gain(plate: PlateSystem, required_snr: int) -> int:
    """The lowest gain that still clears the noise floor, which is the one to use."""
    if required_snr <= 0:
        raise ValidationError("a required figure has to be positive")
    for gain in range(1, 11):
        settings = Settings(pitch=recommended_pitch(plate), gain=gain, latitude=3)
        if effective_snr(plate, settings) >= required_snr:
            return gain
    raise TechniqueError(
        "no gain on this scanner clears that noise floor",
        plate=plate.key,
        required_snr=required_snr,
    )


def settings_for(plate: PlateSystem, required_snr: int) -> Settings:
    """The settings the office would write on the sheet if it wrote them at all."""
    if plate.normalised_snr <= 0:
        raise ConfigurationError("that plate has no noise figure", plate=plate.key)
    return Settings(
        pitch=recommended_pitch(plate),
        gain=lowest_workable_gain(plate, required_snr),
        latitude=3,
    )
