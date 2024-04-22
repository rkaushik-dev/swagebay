from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.exposure.corrections import (
    Correction,
    CorrectionChain,
    collimator_correction,
    distance_correction,
    film_correction,
    material_correction,
    screen_correction,
)
from qu_imager.imaging.film import REFERENCE_FILM, film
from qu_imager.materials import STEEL, Quality, material
from qu_imager.quantities.length import Length, inch


def test_shooting_further_away_multiplies_by_the_square():
    doubled = distance_correction(inch(24), inch(48))
    assert doubled.factor == Decimal(4)
    assert "24 in" in doubled.note


def test_shooting_closer_divides_by_the_square():
    closer = distance_correction(inch(24), inch(12))
    assert closer.factor == Decimal("0.25")


def test_the_same_distance_leaves_the_reading_alone():
    assert distance_correction(inch(24), inch(24)).factor == Decimal(1)


def test_a_distance_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        distance_correction(Length.ZERO, inch(24))
    with pytest.raises(ValidationError):
        distance_correction(inch(24), Length.ZERO)


def test_a_faster_film_shortens_the_shot():
    faster = film_correction(REFERENCE_FILM, film("d8"))
    assert faster.factor < 1
    assert faster.factor == Decimal(100) / Decimal(180)


def test_a_slower_film_lengthens_it():
    slower = film_correction(REFERENCE_FILM, film("d4"))
    assert slower.factor == Decimal(100) / Decimal(32)
    assert film_correction(REFERENCE_FILM, REFERENCE_FILM).factor == Decimal(1)


def test_the_material_factor_is_reported_and_not_folded_in():
    alu = material_correction(STEEL, material("aluminium"), Quality.IR192)
    assert alu.factor == Decimal("0.35")
    assert "aluminium read as carbon steel" in alu.note


def test_a_material_the_chart_was_drawn_for_needs_no_factor():
    assert material_correction(STEEL, STEEL, Quality.IR192).factor == Decimal(1)


def test_screens_halve_the_exposure_when_they_are_fitted():
    assert screen_correction(True).factor == Decimal("0.5")
    assert screen_correction(False).factor == Decimal(1)
    assert screen_correction(True, Decimal("0.4")).factor == Decimal("0.4")


def test_a_collimator_costs_exposure_rather_than_saving_it():
    tight = collimator_correction(Decimal("0.8"))
    assert tight.factor == Decimal("1.25")
    assert collimator_correction(Decimal(1)).factor == Decimal(1)
    with pytest.raises(ValidationError):
        collimator_correction(Decimal(0))
    with pytest.raises(ValidationError):
        collimator_correction(Decimal("1.2"))


def test_a_correction_of_zero_or_less_is_refused():
    with pytest.raises(ValidationError):
        Correction("bad", Decimal(0), "nothing")
    with pytest.raises(ValidationError):
        Correction("bad", Decimal(-1), "nothing")


def test_an_empty_chain_leaves_the_reading_alone():
    chain = CorrectionChain()
    assert chain.combined == Decimal(1)
    assert chain.applied_to(Decimal(30)) == Decimal(30)
    assert chain.describe() == "no corrections"


def test_a_chain_multiplies_in_the_order_it_was_built():
    chain = (
        CorrectionChain()
        .with_correction(distance_correction(inch(24), inch(48)))
        .with_correction(film_correction(REFERENCE_FILM, film("d5")))
    )
    assert chain.names() == ("distance", "film")
    assert chain.combined == Decimal(4) * (Decimal(100) / Decimal(55))
    assert chain.applied_to(Decimal(30)) > 200


def test_a_chain_is_immutable_so_a_sheet_cannot_be_edited_after_the_fact():
    first = CorrectionChain().with_correction(screen_correction(True))
    second = first.with_correction(collimator_correction(Decimal("0.8")))
    assert first.names() == ("screens",)
    assert second.names() == ("screens", "collimator")


def test_opposing_corrections_cancel():
    chain = (
        CorrectionChain()
        .with_correction(distance_correction(inch(24), inch(48)))
        .with_correction(screen_correction(True, Decimal("0.25")))
    )
    assert chain.combined == Decimal(1)


def test_a_chain_will_not_be_applied_to_nothing():
    with pytest.raises(ValidationError):
        CorrectionChain().applied_to(Decimal(0))


def test_the_chain_prints_the_arithmetic_the_sheet_shows():
    chain = CorrectionChain().with_correction(distance_correction(inch(24), inch(48)))
    assert chain.describe() == "distance x4"
    assert str(screen_correction(True)) == "screens x0.5"
