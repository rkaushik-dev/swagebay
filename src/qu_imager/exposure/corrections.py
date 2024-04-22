"""Corrections applied to a chart reading, and a record of every one.

A chart gives curie minutes for one distance, one film and one density. A real
shot is almost never all three, so the reading gets multiplied by a factor for
each thing that differs. That part is arithmetic and it is not hard.

The part that is hard is defending it a year later. A shooter who writes down
one number has to be believed. A shooter who writes down the chart reading and
every factor applied to it can be checked, and the check is where the mistake
turns up: nine times out of ten it is a distance factor applied upside down,
because the correction for shooting further away multiplies and the correction
for a faster film divides, and both feel like they should go the other way.

So corrections carry their own name and their own reason and they are kept in
order. The technique sheet prints the chain. Nothing here collapses to a single
factor until the caller asks for it.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.imaging.film import FilmSystem
from qu_imager.materials import Material, Quality
from qu_imager.quantities.length import Length


@dataclass(frozen=True, slots=True)
class Correction:
    """One factor, with what it was for."""

    name: str
    factor: Decimal
    note: str

    def __post_init__(self) -> None:
        if self.factor <= 0:
            raise ValidationError(
                "a correction factor has to be positive", name=self.name, factor=str(self.factor)
            )

    def __str__(self) -> str:
        return f"{self.name} x{self.factor.normalize()}"


def distance_correction(chart_sfd: Length, actual_sfd: Length) -> Correction:
    """Further away needs longer, by the square of the ratio."""
    if chart_sfd.um <= 0 or actual_sfd.um <= 0:
        raise ValidationError("both distances have to be positive")
    ratio = actual_sfd.ratio(chart_sfd)
    return Correction(
        name="distance",
        factor=ratio * ratio,
        note=f"chart at {chart_sfd.format_inch(0)}, shot at {actual_sfd.format_inch(0)}",
    )


def film_correction(chart_film: FilmSystem, actual_film: FilmSystem) -> Correction:
    """A faster film needs less time, so the factor is the speed ratio inverted."""
    return Correction(
        name="film",
        factor=actual_film.time_factor_from(chart_film),
        note=f"chart on {chart_film.name}, shot on {actual_film.name}",
    )


def material_correction(
    chart_material: Material, actual_material: Material, quality: Quality
) -> Correction:
    """When the chart is steel and the weld is not.

    This one is applied to the thickness before the chart is read, not to the
    exposure after. It is here so the sheet can print it in the same chain, and
    the factor is reported rather than folded in, so nobody applies it twice.
    """
    chart_factor = chart_material.factor_at(quality)
    actual_factor = actual_material.factor_at(quality)
    if chart_factor <= 0:
        raise ValidationError("a chart material needs a positive equivalence factor")
    return Correction(
        name="material",
        factor=actual_factor / chart_factor,
        note=f"{actual_material} read as {chart_material} at {quality}",
    )


def screen_correction(present: bool, factor: Decimal = Decimal("0.5")) -> Correction:
    """Lead screens intensify, so they shorten the shot."""
    return Correction(
        name="screens",
        factor=factor if present else Decimal(1),
        note="lead screens fitted" if present else "no screens",
    )


def collimator_correction(transmission: Decimal) -> Correction:
    """A collimator that clips the beam edge costs a little exposure."""
    if not 0 < transmission <= 1:
        raise ValidationError("collimator transmission sits in (0, 1]")
    return Correction(
        name="collimator",
        factor=Decimal(1) / transmission,
        note=f"beam transmission {transmission.normalize()}",
    )


@dataclass(frozen=True, slots=True)
class CorrectionChain:
    """Every factor applied to one chart reading, in the order applied."""

    corrections: tuple[Correction, ...] = ()

    def with_correction(self, correction: Correction) -> CorrectionChain:
        return CorrectionChain((*self.corrections, correction))

    @property
    def combined(self) -> Decimal:
        total = Decimal(1)
        for correction in self.corrections:
            total *= correction.factor
        return total

    def applied_to(self, curie_minutes: Decimal) -> Decimal:
        if curie_minutes <= 0:
            raise ValidationError("a chart reading has to be positive")
        return curie_minutes * self.combined

    def names(self) -> tuple[str, ...]:
        return tuple(c.name for c in self.corrections)

    def describe(self) -> str:
        if not self.corrections:
            return "no corrections"
        return ", ".join(str(c) for c in self.corrections)
