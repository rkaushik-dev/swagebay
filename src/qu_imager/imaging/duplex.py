"""Duplex wire gauges and the basic spatial resolution they measure.

A duplex gauge is thirteen pairs of wires, each pair separated by a gap equal
to the wire diameter. Lay it on the part, shoot it, and look for the finest
pair that still reads as two wires rather than one grey smear. That pair names
the unsharpness of the whole imaging chain: focal spot, geometry, detector,
scanning, everything, measured at once rather than calculated a piece at a
time.

Half the unsharpness is the basic spatial resolution, which is the number that
goes on a detector qualification and the number a client specification quotes.
The factor of two catches people out constantly and there is no way to make it
intuitive: a pair that reads at 0.40 mm unsharpness is a system with 0.20 mm
basic spatial resolution.

Two rules of thumb are worth knowing. Resolution better than the wire indicator
requires is wasted, and resolution worse than it makes the wire indicator
unreadable no matter how good the contrast is. So a technique that fails on the
wire and passes on the duplex gauge has a contrast problem, and one that fails
on both has a geometry problem, and those get fixed in different ways.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.quantities.length import Length

# pair number -> wire diameter in millimetres, which is also the gap
_DUPLEX: dict[int, str] = {
    1: "0.80",
    2: "0.63",
    3: "0.50",
    4: "0.40",
    5: "0.32",
    6: "0.25",
    7: "0.20",
    8: "0.16",
    9: "0.13",
    10: "0.10",
    11: "0.08",
    12: "0.063",
    13: "0.05",
}


@dataclass(frozen=True, slots=True)
class DuplexPair:
    """One pair on the gauge."""

    number: int

    def __post_init__(self) -> None:
        if self.number not in _DUPLEX:
            raise ConfigurationError("no such duplex pair", number=self.number)

    @property
    def wire_diameter(self) -> Length:
        return Length.from_mm(_DUPLEX[self.number])

    @property
    def unsharpness(self) -> Length:
        """Wire plus gap, which is what the pair resolves to."""
        return self.wire_diameter * 2

    @property
    def basic_spatial_resolution(self) -> Length:
        """Half the unsharpness. This is the number on the certificate."""
        return self.wire_diameter

    @property
    def label(self) -> str:
        return f"D{self.number}"

    def __str__(self) -> str:
        return self.label


def pairs() -> tuple[DuplexPair, ...]:
    return tuple(DuplexPair(n) for n in sorted(_DUPLEX))


def finest_resolved(number: int) -> DuplexPair:
    """The reader names a pair number. This is that pair."""
    return DuplexPair(number)


def resolution_from_reading(number: int) -> Length:
    return DuplexPair(number).basic_spatial_resolution


def pair_for_resolution(target: Length) -> DuplexPair:
    """The coarsest pair that still meets a required resolution."""
    if target.um <= 0:
        raise ValidationError("a resolution target has to be positive")
    best: DuplexPair | None = None
    for pair in pairs():
        if pair.basic_spatial_resolution <= target:
            best = pair
            break
    if best is None:
        raise ConfigurationError(
            "no duplex pair is fine enough for that target", target_mm=str(target.mm)
        )
    return best


def meets(reading: int, required: Length) -> bool:
    return resolution_from_reading(reading) <= required


def unsharpness_budget(geometric: Length, detector: Length) -> Length:
    """The two unsharpness terms add in quadrature, not straight.

    Adding them straight overstates the total and has retired techniques that
    would have worked. The quadrature sum is what the standards use and it is
    what a duplex gauge actually measures.
    """
    if geometric.um < 0 or detector.um < 0:
        raise ValidationError("an unsharpness term cannot be negative")
    total = Decimal(geometric.um) ** 2 + Decimal(detector.um) ** 2
    return Length(int(total.sqrt()))
