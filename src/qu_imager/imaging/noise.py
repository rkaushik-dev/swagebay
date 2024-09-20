"""Signal to noise, and why the raw figure is not comparable to anything.

Measuring signal to noise on a digital image is easy. Put a box on a uniform
part of the image, take the mean grey value and the standard deviation, divide
one by the other. Every viewer does it.

Comparing two of those numbers is where it goes wrong. A coarse system averages
over a bigger area per pixel, so it reports a better signal to noise for the
same physics, and a panel with twice the pixel size can look twice as good
while showing half as much. The standard fixes that by normalising to a
reference basic spatial resolution of 88.6 micrometres:

    SNRn = SNR x sqrt(88.6 / SRb in micrometres)

A fine system gets scaled up, a coarse one gets scaled down, and after that the
numbers mean the same thing on both. Anybody quoting a raw signal to noise for
a digital technique is either being careless or hoping nobody checks.

The required figure depends on penetrated thickness and on the technique class,
and it is the number a client argues about, so the table is here as data and it
carries the class it came from.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.selection import TechniqueClass
from qu_imager.quantities.length import Length

REFERENCE_RESOLUTION = Length.from_mm("0.0886")
"""The 88.6 micrometre reference every normalised figure is scaled to."""


@dataclass(frozen=True, slots=True)
class NoiseMeasurement:
    """One box drawn on one image."""

    label: str
    mean_grey: Decimal
    standard_deviation: Decimal
    resolution: Length

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("a noise measurement needs a location")
        if self.mean_grey <= 0:
            raise ValidationError("a mean grey value has to be positive", label=self.label)
        if self.standard_deviation <= 0:
            raise ValidationError("noise of zero is not a measurement", label=self.label)
        if self.resolution.um <= 0:
            raise ValidationError("a measurement needs the resolution it was made at")

    @property
    def signal_to_noise(self) -> Decimal:
        return self.mean_grey / self.standard_deviation

    @property
    def normalised(self) -> Decimal:
        return normalise(self.signal_to_noise, self.resolution)

    def __str__(self) -> str:
        return f"{self.label} snr {self.signal_to_noise:.1f} normalised {self.normalised:.1f}"


def normalise(signal_to_noise: Decimal, resolution: Length) -> Decimal:
    """Scale a raw figure to the reference resolution."""
    if signal_to_noise <= 0:
        raise ValidationError("a signal to noise figure has to be positive")
    if resolution.um <= 0:
        raise ValidationError("a resolution has to be positive")
    with localcontext() as ctx:
        ctx.prec = 28
        ratio = Decimal(REFERENCE_RESOLUTION.um) / Decimal(resolution.um)
        return signal_to_noise * ratio.sqrt()


def denormalise(normalised_snr: Decimal, resolution: Length) -> Decimal:
    """What a system at that resolution has to read raw to reach a normalised figure."""
    if normalised_snr <= 0:
        raise ValidationError("a normalised figure has to be positive")
    if resolution.um <= 0:
        raise ValidationError("a resolution has to be positive")
    with localcontext() as ctx:
        ctx.prec = 28
        ratio = Decimal(resolution.um) / Decimal(REFERENCE_RESOLUTION.um)
        return normalised_snr * ratio.sqrt()


# penetrated thickness ceiling in mm, minimum normalised snr
_REQUIRED_A: tuple[tuple[str, int], ...] = (
    ("10", 70),
    ("20", 80),
    ("40", 90),
    ("100", 100),
    ("250", 110),
)

_REQUIRED_B: tuple[tuple[str, int], ...] = (
    ("10", 100),
    ("20", 110),
    ("40", 120),
    ("100", 130),
    ("250", 140),
)


def required_normalised_snr(penetrated: Length, technique_class: TechniqueClass) -> int:
    """The floor for that thickness and class."""
    if penetrated.um <= 0:
        raise ValidationError("a penetrated thickness has to be positive")
    table = _REQUIRED_B if technique_class.is_improved else _REQUIRED_A
    for ceiling, floor in table:
        if penetrated <= Length.from_mm(ceiling):
            return floor
    raise TechniqueError(
        "that penetrated thickness is past the end of the noise table",
        penetrated_mm=str(penetrated.mm),
        technique_class=technique_class.value,
    )


def meets_requirement(
    measurement: NoiseMeasurement, penetrated: Length, technique_class: TechniqueClass
) -> bool:
    return measurement.normalised >= required_normalised_snr(penetrated, technique_class)


def shortfall(
    measurement: NoiseMeasurement, penetrated: Length, technique_class: TechniqueClass
) -> Decimal:
    """How far under the floor the image is. Zero when it clears."""
    floor = required_normalised_snr(penetrated, technique_class)
    gap = Decimal(floor) - measurement.normalised
    return gap if gap > 0 else Decimal(0)
