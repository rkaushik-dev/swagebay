"""Viewing conditions, which decide whether a good radiograph can be read at all.

A film with a density of 3.5 is a good film. It is also almost opaque, and
getting enough light through it to see anything takes a viewer that most shops
do not own. So the density a technique aims for and the viewer the film will be
read on are the same decision, and treating them separately is how a shop ends
up with a filing cabinet of perfectly exposed film nobody can interpret.

The rule is luminance behind the film. A viewer with a stated maximum
luminance can support densities up to the point where the light coming through
falls below what an eye can work with, which the standards put at about thirty
candela per square metre. Above that density the film is dark to the reader
however good it is.

    supported density = log10(viewer luminance / 30)

The ambient light in the room is the other half and it is the half nobody
measures. A bright room raises the light scattered off the front of the film,
which washes out the contrast that the density was bought to produce. The rule
of thumb in the written practice is that the room has to be dark enough that
the film front reflects less than a hundredth of what comes through it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext

from qu_imager.errors import ConfigurationError, ValidationError

MINIMUM_TRANSMITTED = Decimal(30)
"""Candela a square metre through the film, below which nobody reads anything."""

MAXIMUM_AMBIENT_FRACTION = Decimal("0.01")
"""How much of the transmitted light the room may add by reflection."""


@dataclass(frozen=True, slots=True)
class Viewer:
    """An illuminator, as its plate says."""

    key: str
    name: str
    maximum_luminance: int
    """Candela a square metre with nothing in front of it."""
    masked: bool

    def __post_init__(self) -> None:
        if self.maximum_luminance <= 0:
            raise ValidationError("a viewer has a luminance", key=self.key)

    @property
    def supported_density(self) -> Decimal:
        """The densest film this viewer can push enough light through."""
        with localcontext() as ctx:
            ctx.prec = 28
            ratio = Decimal(self.maximum_luminance) / MINIMUM_TRANSMITTED
            return ratio.ln() / Decimal(10).ln()

    def supports(self, density: Decimal) -> bool:
        return density <= self.supported_density

    def transmitted_at(self, density: Decimal) -> Decimal:
        """The light that reaches the reader through a film of that density."""
        if density < 0:
            raise ValidationError("a density is not negative")
        with localcontext() as ctx:
            ctx.prec = 28
            return Decimal(self.maximum_luminance) / (Decimal(10) ** density)

    def __str__(self) -> str:
        masking = "masked" if self.masked else "unmasked"
        return f"{self.name} {self.maximum_luminance} cd/m2 {masking}"


def maximum_ambient(viewer: Viewer, density: Decimal) -> Decimal:
    """How dark the room has to be for a film of that density to be readable."""
    return viewer.transmitted_at(density) * MAXIMUM_AMBIENT_FRACTION


def faults(viewer: Viewer, density: Decimal, ambient: Decimal) -> tuple[str, ...]:
    """Everything wrong with reading that film on that viewer in that room."""
    out: list[str] = []
    if not viewer.supports(density):
        out.append(
            f"density {density} is over the {viewer.supported_density:.2f} this viewer supports"
        )
    allowed = maximum_ambient(viewer, density)
    if ambient > allowed:
        out.append(f"ambient light {ambient} is over the {allowed:.3f} allowed")
    if not viewer.masked and density < Decimal("2.0"):
        out.append("a thin film on an unmasked viewer is washed out by the surround")
    return tuple(out)


_VIEWERS: dict[str, Viewer] = {
    v.key: v
    for v in [
        Viewer("hi300", "HiLight 300", 30_000, True),
        Viewer("hi100", "HiLight 100", 10_000, True),
        Viewer("bench", "bench box", 3_000, False),
        Viewer("field", "field viewer", 1_000, False),
    ]
}


def viewer(key: str) -> Viewer:
    try:
        return _VIEWERS[key]
    except KeyError:
        raise ConfigurationError("no such viewer", key=key) from None


def known_viewers() -> tuple[str, ...]:
    return tuple(sorted(_VIEWERS))


def viewers_supporting(density: Decimal) -> tuple[Viewer, ...]:
    """Every viewer in the shop that can read a film that dense, dimmest first."""
    fits = [v for v in _VIEWERS.values() if v.supports(density)]
    return tuple(sorted(fits, key=lambda v: v.maximum_luminance))
