"""X-ray tubes, which are the other half of the job and behave nothing like a source.

A gamma source is a fixed lump of activity that decays. A tube is a machine
with two knobs, and everything about planning with one is different.

Kilovoltage sets the penetrating power and it is chosen for the thickness. Too
low and nothing gets through. Too high and the contrast collapses, because a
harder beam is less selective about what it passes, and a radiograph with no
contrast passes the density check and shows nothing. So the rule is the lowest
kilovoltage that will penetrate, which is the opposite of what anybody expects.

Milliampere seconds set the quantity and scale linearly. Double the mA or
double the time and you have doubled the exposure, which makes tube arithmetic
much friendlier than isotope arithmetic where the source is also decaying.

The focal spot is the third number and it is the one on the certificate. A tube
with a 1.5 mm focal spot cannot make the same geometry as one with a 0.4 mm
spot, and a shop that owns one of each has two very different sets of technique
sheets.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.materials import Quality
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Tube:
    """An x-ray set, as its certificate describes it."""

    key: str
    name: str
    manufacturer: str
    min_kv: int
    max_kv: int
    max_ma: Decimal
    focal_spot: Length
    duty_cycle: Decimal
    """Fraction of a cycle the set may be energised for, which limits a long shot."""

    def __post_init__(self) -> None:
        if self.min_kv <= 0 or self.max_kv < self.min_kv:
            raise ValidationError("a tube has a kilovoltage range", key=self.key)
        if self.max_ma <= 0:
            raise ValidationError("a tube has a current", key=self.key)
        if self.focal_spot.um <= 0:
            raise ValidationError("a tube has a focal spot", key=self.key)
        if not 0 < self.duty_cycle <= 1:
            raise ValidationError("a duty cycle sits in (0, 1]", key=self.key)

    def can_reach(self, kv: int) -> bool:
        return self.min_kv <= kv <= self.max_kv

    def check_kv(self, kv: int) -> None:
        if not self.can_reach(kv):
            raise TechniqueError(
                "that kilovoltage is outside the set",
                tube=self.key,
                kv=kv,
                range=f"{self.min_kv} to {self.max_kv}",
            )

    def check_current(self, ma: Decimal) -> None:
        if ma <= 0:
            raise ValidationError("a current has to be positive", ma=str(ma))
        if ma > self.max_ma:
            raise TechniqueError(
                "that current is over what the set will give",
                tube=self.key,
                ma=str(ma),
                max_ma=str(self.max_ma),
            )

    def cooling_after(self, energised: Duration) -> Duration:
        """How long the set has to rest before the next shot of that length."""
        rest = (Decimal(1) - self.duty_cycle) / self.duty_cycle
        return Duration(int(Decimal(energised.ms) * rest))

    def quality_at(self, kv: int) -> Quality:
        """Which equivalence table column this beam reads on."""
        self.check_kv(kv)
        for ceiling, quality in _QUALITY_STEPS:
            if kv <= ceiling:
                return quality
        return Quality.MV1

    def __str__(self) -> str:
        return f"{self.name} {self.min_kv} to {self.max_kv} kV"


_QUALITY_STEPS: tuple[tuple[int, Quality], ...] = (
    (100, Quality.KV100),
    (150, Quality.KV150),
    (220, Quality.KV220),
    (400, Quality.KV400),
)


def milliampere_seconds(current_ma: Decimal, span: Duration) -> Decimal:
    """The quantity knob, which scales linearly in both of its inputs."""
    if current_ma <= 0:
        raise ValidationError("a current has to be positive")
    return current_ma * span.seconds


def time_for(current_ma: Decimal, mas: Decimal) -> Duration:
    """How long to run at that current to reach a required exposure."""
    if current_ma <= 0:
        raise ValidationError("a current has to be positive")
    if mas <= 0:
        raise ValidationError("an exposure has to be positive")
    return Duration.from_seconds(mas / current_ma).round_up_to_second()


_TUBES: dict[str, Tube] = {
    t.key: t
    for t in [
        Tube(
            key="eresco42",
            name="ERESCO 42 MF4",
            manufacturer="Waygate",
            min_kv=10,
            max_kv=200,
            max_ma=Decimal(10),
            focal_spot=Length.from_mm("1.0"),
            duty_cycle=Decimal("0.5"),
        ),
        Tube(
            key="eresco65",
            name="ERESCO 65 MF4",
            manufacturer="Waygate",
            min_kv=15,
            max_kv=300,
            max_ma=Decimal(10),
            focal_spot=Length.from_mm("3.0"),
            duty_cycle=Decimal("0.5"),
        ),
        Tube(
            key="xrs3",
            name="XRS-3",
            manufacturer="Golden",
            min_kv=270,
            max_kv=270,
            max_ma=Decimal("0.1"),
            focal_spot=Length.from_mm("3.0"),
            duty_cycle=Decimal("0.2"),
        ),
        Tube(
            key="mg452",
            name="MG452",
            manufacturer="Comet",
            min_kv=20,
            max_kv=450,
            max_ma=Decimal(15),
            focal_spot=Length.from_mm("5.5"),
            duty_cycle=Decimal("1.0"),
        ),
    ]
}


def tube(key: str) -> Tube:
    try:
        return _TUBES[key]
    except KeyError:
        raise ConfigurationError("no such x-ray set", key=key) from None


def known_tubes() -> tuple[str, ...]:
    return tuple(sorted(_TUBES))


def tubes_reaching(kv: int) -> tuple[Tube, ...]:
    """Every set in the shop that will make that kilovoltage, finest spot first."""
    fits = [t for t in _TUBES.values() if t.can_reach(kv)]
    return tuple(sorted(fits, key=lambda t: t.focal_spot))
