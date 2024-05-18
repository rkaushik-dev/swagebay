"""One view over the three things a radiograph can be recorded on.

Film, a phosphor plate and a detector panel are not interchangeable and this
module does not pretend they are. What it does is give the planner one handle,
because most of the technique is the same whichever is used: the same geometry,
the same indicator, the same coverage.

Where they differ is the gate applied afterwards, and the difference is not a
detail. Film is graded on density and the density rule is meaningless on a
digital image, which has no density and whose grey values can be windowed to
anything. Digital is graded on normalised signal to noise, which cannot be
measured on film at all. A system that applied both gates to everything would
reject every radiograph ever made.

So the medium decides which gate runs. That is the one branch in this package
that genuinely has to exist, and putting it here keeps it out of everywhere
else.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from qu_imager.errors import ValidationError
from qu_imager.imaging.computed import PlateSystem
from qu_imager.imaging.detector import DetectorArray
from qu_imager.imaging.film import FilmSystem
from qu_imager.quantities.length import Length

FILM_RESOLUTION = Length.from_mm("0.030")
"""What a fine grain film with lead screens resolves in practice."""


class Medium(Enum):
    FILM = "film"
    PLATE = "computed radiography"
    PANEL = "digital detector array"

    @property
    def graded_on_density(self) -> bool:
        return self is Medium.FILM

    @property
    def graded_on_noise(self) -> bool:
        return self is not Medium.FILM

    @property
    def is_digital(self) -> bool:
        return self is not Medium.FILM

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class ImagingSystem:
    """Whatever the image is going on, described the way planning needs it."""

    key: str
    name: str
    medium: Medium
    basic_spatial_resolution: Length
    speed_index: Decimal | None = None
    normalised_snr: int | None = None

    def __post_init__(self) -> None:
        if self.basic_spatial_resolution.um <= 0:
            raise ValidationError("an imaging system needs a resolution", key=self.key)
        if self.medium.graded_on_density and self.speed_index is None:
            raise ValidationError("a film system needs a speed index", key=self.key)
        if self.medium.graded_on_noise and self.normalised_snr is None:
            raise ValidationError("a digital system needs a noise figure", key=self.key)

    @classmethod
    def from_film(cls, film: FilmSystem) -> ImagingSystem:
        return cls(
            key=film.key,
            name=film.name,
            medium=Medium.FILM,
            basic_spatial_resolution=FILM_RESOLUTION,
            speed_index=film.speed_index,
        )

    @classmethod
    def from_plate(cls, plate: PlateSystem) -> ImagingSystem:
        return cls(
            key=plate.key,
            name=plate.name,
            medium=Medium.PLATE,
            basic_spatial_resolution=plate.basic_spatial_resolution,
            normalised_snr=plate.normalised_snr,
        )

    @classmethod
    def from_detector(cls, panel: DetectorArray) -> ImagingSystem:
        return cls(
            key=panel.key,
            name=panel.name,
            medium=Medium.PANEL,
            basic_spatial_resolution=panel.basic_spatial_resolution,
            normalised_snr=panel.normalised_snr,
        )

    @property
    def gates(self) -> tuple[str, ...]:
        """Which checks a radiograph on this system has to pass."""
        if self.medium.graded_on_density:
            return ("density", "density variation", "indicator")
        return ("normalised snr", "basic spatial resolution", "indicator")

    def resolves(self, feature: Length) -> bool:
        return feature >= self.basic_spatial_resolution

    def __str__(self) -> str:
        return f"{self.name} on {self.medium}"


def density_applies(system: ImagingSystem) -> bool:
    return system.medium.graded_on_density


def noise_applies(system: ImagingSystem) -> bool:
    return system.medium.graded_on_noise
