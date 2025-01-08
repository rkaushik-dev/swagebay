"""Lead screens, filters and the backscatter test.

A lead screen does two things at once and they pull in opposite directions.
Electrons knocked out of the lead by the beam expose the film, which
intensifies the image and shortens the shot. Scattered radiation from
everything around is softer than the primary beam, and the lead absorbs it,
which sharpens the image. The first effect wants thin lead and the second wants
thick, so every quality has its own thickness and using the iridium screen on a
hundred kilovolt shot fogs the film rather than helping it.

Filtration is the tube side of the same argument. A sheet of copper or
aluminium at the port absorbs the softest part of the spectrum, which would
never have got through the part anyway and only adds scatter. The shot gets
longer and the image gets cleaner, and on thick sections it is worth it.

Backscatter is the one nobody can calculate, so it is measured. A lead letter B
goes on the back of the cassette, and if its image shows through as a lighter
mark on the developed film then radiation is coming back off the floor and the
shot has to be repeated with a lead sheet behind it. The test is crude and it
has never been improved on.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.materials import Quality
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Screens:
    """A front and back lead screen pair for one radiation quality."""

    quality: Quality
    front: Length
    back: Length
    intensification: Decimal

    def __post_init__(self) -> None:
        if self.front.um < 0 or self.back.um < 0:
            raise ValidationError("a screen cannot be a negative thickness")
        if self.intensification < 1:
            raise ValidationError(
                "a screen does not make a shot longer", factor=str(self.intensification)
            )

    @property
    def time_factor(self) -> Decimal:
        """Multiply a chart time by this when the screens are fitted."""
        return Decimal(1) / self.intensification

    def suits(self, quality: Quality) -> bool:
        return quality is self.quality

    def describe(self) -> str:
        return (
            f"{self.front.format_mm(2)} front, {self.back.format_mm(2)} back, "
            f"x{self.intensification} faster"
        )


_SCREENS: dict[Quality, Screens] = {
    s.quality: s
    for s in [
        Screens(Quality.KV100, Length.from_mm("0.02"), Length.from_mm("0.10"), Decimal("1.2")),
        Screens(Quality.KV150, Length.from_mm("0.05"), Length.from_mm("0.10"), Decimal("1.5")),
        Screens(Quality.KV220, Length.from_mm("0.10"), Length.from_mm("0.15"), Decimal("2.0")),
        Screens(Quality.KV400, Length.from_mm("0.10"), Length.from_mm("0.15"), Decimal("2.5")),
        Screens(Quality.IR192, Length.from_mm("0.13"), Length.from_mm("0.15"), Decimal("2.5")),
        Screens(Quality.CO60, Length.from_mm("0.25"), Length.from_mm("0.25"), Decimal("3.0")),
    ]
}


def screens_for(quality: Quality) -> Screens:
    try:
        return _SCREENS[quality]
    except KeyError:
        raise ConfigurationError(
            "no screen pair is specified for that quality", quality=quality.value
        ) from None


def qualities_with_screens() -> tuple[Quality, ...]:
    return tuple(sorted(_SCREENS, key=lambda q: q.name))


def wrong_screens(fitted: Screens, quality: Quality) -> str | None:
    """Why a screen pair does not belong on this shot, if it does not."""
    if fitted.suits(quality):
        return None
    wanted = screens_for(quality)
    if fitted.front < wanted.front:
        return (
            f"{fitted.front.format_mm(2)} of front lead is thin for {quality}, "
            f"which wants {wanted.front.format_mm(2)} and will fog"
        )
    return (
        f"{fitted.front.format_mm(2)} of front lead is heavy for {quality}, "
        f"which wants {wanted.front.format_mm(2)} and will lose speed"
    )


@dataclass(frozen=True, slots=True)
class Filter:
    """A sheet at the tube port that takes the soft end off the beam."""

    material: str
    thickness: Length
    time_multiplier: Decimal

    def __post_init__(self) -> None:
        if self.thickness.um <= 0:
            raise ValidationError("a filter has a thickness")
        if self.time_multiplier < 1:
            raise ValidationError("a filter makes a shot longer, never shorter")

    def describe(self) -> str:
        return f"{self.thickness.format_mm(2)} {self.material}, x{self.time_multiplier}"


def recommended_filter(steel_thickness: Length) -> Filter | None:
    """Above this much steel the soft end of the beam is only making scatter."""
    if steel_thickness.um <= 0:
        raise ValidationError("a thickness has to be positive")
    if steel_thickness < Length.from_mm(25):
        return None
    if steel_thickness < Length.from_mm(50):
        return Filter("copper", Length.from_mm("0.5"), Decimal("1.4"))
    return Filter("copper", Length.from_mm("1.0"), Decimal("2.0"))


@dataclass(frozen=True, slots=True)
class BackscatterTest:
    """The lead letter B, as the reader found it."""

    letter_visible: bool
    lighter_than_background: bool

    @property
    def failed(self) -> bool:
        """Only a lighter image is a failure. A darker one is not backscatter."""
        return self.letter_visible and self.lighter_than_background

    def verdict(self) -> str:
        if not self.letter_visible:
            return "no backscatter"
        if not self.lighter_than_background:
            return "the letter shows darker, which is not backscatter"
        return "backscatter, put lead behind the cassette and shoot it again"
