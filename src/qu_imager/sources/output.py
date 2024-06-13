"""How hot a source is at a distance.

The whole of field radiation safety is one equation and it is not a
complicated one:

    rate = gamma constant x curies / metres squared

The gamma constant is a property of the isotope and it is quoted in roentgen an
hour at one metre from one curie, which is a mouthful and an ugly mixture of
units, and it is what every card in every truck is printed in so it is what
this module works in.

The inverse square is the part crews get wrong, and always in the same
direction. Halving the distance quadruples the rate. A radiographer who steps
from four metres to two has not doubled his dose, he has quadrupled it. The
useful form of that is the other way round: to cut a rate by a hundred, back
off by ten.

The law holds for a point source in air with nothing in the way. Every one of
those is an approximation on a real job. Close to the source it overestimates,
because the source is not a point. Far away in a plant it underestimates,
because concrete scatters. Both errors are on the safe side at the distances
boundaries are drawn at, which is the reason nobody has ever bothered to fix
it.

This module gives the bare rate and nothing else. Working a boundary distance
out of it means knowing what is between the source and the person, and that is
a field, not an isotope. It lives in safety.field and there is deliberately no
second copy of the arithmetic here.
"""

from __future__ import annotations

from decimal import localcontext

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.sources.isotope import Isotope

ONE_METRE = Length.from_m(1)


def output_at_one_metre(source: Isotope, activity: Activity) -> DoseRate:
    """The rate at a metre with nothing in the way."""
    roentgen_per_hour = source.gamma_constant * activity.curie
    return DoseRate.from_mr_per_hour(roentgen_per_hour * 1000)


def dose_rate_at(source: Isotope, activity: Activity, distance: Length) -> DoseRate:
    """The unshielded rate at any distance."""
    if distance.um <= 0:
        raise ValidationError("a dose rate needs a positive distance", um=distance.um)
    metres = distance.m
    with localcontext() as ctx:
        ctx.prec = 28
        rate = output_at_one_metre(source, activity).nsv_per_hour / (metres * metres)
    return DoseRate(int(rate))
