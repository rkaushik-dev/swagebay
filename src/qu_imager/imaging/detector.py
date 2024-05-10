"""Digital detector arrays, as their characterization report describes them.

A panel is a grid of photodiodes under a scintillator and it has more ways to
be wrong than film has. Film that is fogged is uniformly fogged. A panel can be
sharp in one corner and soft in another, can remember the last exposure for
several frames, can saturate without saying so, and has a population of pixels
that never worked and never will.

So a panel is qualified rather than trusted, and the qualification is a set of
independent numbers that each fail differently:

Basic spatial resolution is not the pixel pitch. It is worse, because the
scintillator spreads light sideways, and a panel sold as a hundred and
twenty seven micron pitch resolves nearer two hundred. Quoting pitch as
resolution is the most common lie in a detector datasheet.

Efficiency says how much dose it takes to reach a given signal to noise, which
decides exposure time the way film speed does.

Image lag is the fraction of the previous frame still present in this one. It
matters on a panel shooting a series of welds quickly and it does not matter at
all on a panel that sits for a minute between shots.

Burn in is lag that never goes away, and a panel with burn in is scrap.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.length import Length

MAX_ACCEPTABLE_LAG = Decimal("0.05")
MAX_ACCEPTABLE_BURN_IN = Decimal("0.01")


@dataclass(frozen=True, slots=True)
class DetectorArray:
    """One panel, characterized to E2597."""

    key: str
    name: str
    manufacturer: str
    pixel_pitch: Length
    basic_spatial_resolution: Length
    normalised_snr: int
    efficiency: Decimal
    image_lag: Decimal
    burn_in: Decimal
    columns: int
    rows: int

    def __post_init__(self) -> None:
        if self.pixel_pitch.um <= 0:
            raise ValidationError("a panel needs a pixel pitch", key=self.key)
        if self.basic_spatial_resolution < self.pixel_pitch:
            raise ValidationError(
                "resolution cannot be finer than the pitch that produced it",
                key=self.key,
                pitch_um=self.pixel_pitch.um,
                resolution_um=self.basic_spatial_resolution.um,
            )
        if self.columns <= 0 or self.rows <= 0:
            raise ValidationError("a panel has a size in pixels", key=self.key)
        for name, value in (("image_lag", self.image_lag), ("burn_in", self.burn_in)):
            if not 0 <= value <= 1:
                raise ValidationError("a fraction sits in [0, 1]", key=self.key, field=name)

    @property
    def pixels(self) -> int:
        return self.columns * self.rows

    @property
    def resolution_penalty(self) -> Decimal:
        """How much worse the resolution is than the pitch would suggest."""
        return self.basic_spatial_resolution.ratio(self.pixel_pitch)

    @property
    def active_area(self) -> tuple[Length, Length]:
        return (self.pixel_pitch * self.columns, self.pixel_pitch * self.rows)

    def lag_is_acceptable(self) -> bool:
        return self.image_lag <= MAX_ACCEPTABLE_LAG

    def is_scrap(self) -> bool:
        """Burn in does not wash out, so a panel with it is finished."""
        return self.burn_in > MAX_ACCEPTABLE_BURN_IN

    def shortfalls(self, required_resolution: Length, required_snr: int) -> tuple[str, ...]:
        out: list[str] = []
        if self.basic_spatial_resolution > required_resolution:
            out.append(
                f"resolution {self.basic_spatial_resolution.format_mm(3)} is coarser than "
                f"{required_resolution.format_mm(3)}"
            )
        if self.normalised_snr < required_snr:
            out.append(f"normalised snr {self.normalised_snr} is under {required_snr}")
        if not self.lag_is_acceptable():
            out.append(f"image lag {self.image_lag} is over {MAX_ACCEPTABLE_LAG}")
        if self.is_scrap():
            out.append(f"burn in {self.burn_in} is over {MAX_ACCEPTABLE_BURN_IN}")
        return tuple(out)

    def __str__(self) -> str:
        return f"{self.name} {self.columns}x{self.rows}"


_PANELS: dict[str, DetectorArray] = {
    d.key: d
    for d in [
        DetectorArray(
            key="dxr250",
            name="DXR250C",
            manufacturer="Waygate",
            pixel_pitch=Length.from_mm("0.200"),
            basic_spatial_resolution=Length.from_mm("0.250"),
            normalised_snr=250,
            efficiency=Decimal("0.72"),
            image_lag=Decimal("0.02"),
            burn_in=Decimal("0.002"),
            columns=1024,
            rows=1024,
        ),
        DetectorArray(
            key="xrd1611",
            name="XRD 1611",
            manufacturer="Perkin",
            pixel_pitch=Length.from_mm("0.100"),
            basic_spatial_resolution=Length.from_mm("0.160"),
            normalised_snr=200,
            efficiency=Decimal("0.64"),
            image_lag=Decimal("0.03"),
            burn_in=Decimal("0.004"),
            columns=4096,
            rows=4096,
        ),
        DetectorArray(
            key="flex7",
            name="Flex 7",
            manufacturer="Waygate",
            pixel_pitch=Length.from_mm("0.127"),
            basic_spatial_resolution=Length.from_mm("0.200"),
            normalised_snr=180,
            efficiency=Decimal("0.58"),
            image_lag=Decimal("0.08"),
            burn_in=Decimal("0.003"),
            columns=2048,
            rows=1536,
        ),
    ]
}


def detector(key: str) -> DetectorArray:
    try:
        return _PANELS[key]
    except KeyError:
        raise ConfigurationError("no such detector array", key=key) from None


def known_detectors() -> tuple[str, ...]:
    return tuple(sorted(_PANELS))


def detectors_meeting(required_resolution: Length, required_snr: int) -> tuple[DetectorArray, ...]:
    fits = [d for d in _PANELS.values() if not d.shortfalls(required_resolution, required_snr)]
    return tuple(sorted(fits, key=lambda d: d.basic_spatial_resolution, reverse=True))
