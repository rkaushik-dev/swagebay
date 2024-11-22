from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.exposure.kilovoltage import (
    CONTRAST_STEP,
    choose,
    contrast_penalty,
    lowest_workable,
    table_bands,
)
from qu_imager.quantities.length import Length, mm
from qu_imager.sources.xray import tube


def test_the_table_gives_the_lowest_that_will_penetrate():
    assert lowest_workable(mm(6)) == 120
    assert lowest_workable(mm(13)) == 150
    assert lowest_workable(mm(25)) == 200


def test_the_bands_are_closed_at_the_top():
    assert lowest_workable(mm(10)) == 140
    assert lowest_workable(mm("10.1")) == 150


def test_a_thicker_part_needs_a_harder_beam():
    values = [lowest_workable(mm(t)) for t in (3, 10, 25, 50, 80)]
    assert values == sorted(values)


def test_a_thickness_past_the_table_is_refused():
    with pytest.raises(TechniqueError) as caught:
        lowest_workable(mm(100))
    assert caught.value.context["table_ends_at_mm"] == "80"


def test_a_thickness_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        lowest_workable(Length.ZERO)


def test_a_choice_that_matches_the_table_says_so():
    picked = choose(mm(13), tube("eresco42"))
    assert picked.kv == 150
    assert picked.is_table_value
    assert picked.reasons == ()
    assert picked.describe() == "150 kV"


def test_a_set_with_a_floor_pushes_the_choice_up_and_says_why():
    picked = choose(mm(3), tube("xrs3"))
    assert picked.kv == 270
    assert picked.from_table == 90
    assert "will not go below 270" in picked.reasons[0]


def test_a_higher_density_costs_a_step():
    plain = choose(mm(13), tube("eresco42"), density=Decimal("2.0"))
    dense = choose(mm(13), tube("eresco42"), density=Decimal("2.5"))
    assert dense.kv == plain.kv + CONTRAST_STEP
    assert "density" in dense.reasons[0]


def test_a_set_that_cannot_reach_the_thickness_is_refused():
    with pytest.raises(TechniqueError) as caught:
        choose(mm(65), tube("eresco42"))
    assert caught.value.context["max_kv"] == 200


def test_a_specification_cap_holds_the_choice_down():
    capped = choose(mm(13), tube("eresco42"), density=Decimal("2.5"), ceiling_kv=150)
    assert capped.kv == 150
    assert "caps it at 150" in capped.reasons[-1]


def test_a_cap_below_what_will_penetrate_is_refused():
    with pytest.raises(TechniqueError) as caught:
        choose(mm(25), tube("eresco42"), ceiling_kv=120)
    assert caught.value.context["needed"] == 200


def test_a_cap_above_the_choice_changes_nothing():
    picked = choose(mm(13), tube("eresco42"), ceiling_kv=300)
    assert picked.kv == 150
    assert picked.is_table_value


def test_the_steps_above_the_table_are_counted():
    plain = choose(mm(13), tube("eresco42"))
    dense = choose(mm(13), tube("eresco42"), density=Decimal("2.5"))
    assert plain.steps_above_table == 0
    assert dense.steps_above_table == 1


def test_the_contrast_penalty_is_zero_at_the_table_value():
    assert contrast_penalty(choose(mm(13), tube("eresco42"))) == Decimal(0)


def test_the_contrast_penalty_grows_with_the_steps():
    dense = choose(mm(13), tube("eresco42"), density=Decimal("2.5"))
    assert contrast_penalty(dense) > Decimal(0)


def test_the_choice_prints_what_it_gave_up_and_why():
    dense = choose(mm(13), tube("eresco42"), density=Decimal("2.5"))
    text = dense.describe()
    assert text.startswith("170 kV, up from 150")
    assert "density" in text


def test_the_table_rises_in_both_columns():
    bands = table_bands()
    assert [b[0] for b in bands] == sorted(b[0] for b in bands)
    assert [b[1] for b in bands] == sorted(b[1] for b in bands)
    assert len(bands) == 11
