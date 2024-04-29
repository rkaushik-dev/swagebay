import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.wire_selection import (
    TechniqueClass,
    bands,
    class_b_is_stricter,
    table_ceiling,
    wire_selection,
)
from qu_imager.quantities.length import Length, mm

A = TechniqueClass.A
B = TechniqueClass.B


def test_a_ten_millimetre_penetrated_thickness_takes_w13_in_class_a():
    picked = wire_selection(mm(10), A)
    assert picked.wire.number == 13
    assert picked.wire.diameter == mm("0.20")


def test_class_b_asks_for_a_finer_wire_at_the_same_thickness():
    basic = wire_selection(mm(10), A)
    improved = wire_selection(mm(10), B)
    assert improved.wire.number > basic.wire.number
    assert improved.wire.diameter < basic.wire.diameter
    assert class_b_is_stricter(mm(10))


def test_the_bands_are_closed_at_the_top():
    assert wire_selection(mm("3.5"), A).wire.number == 16
    assert wire_selection(mm("3.51"), A).wire.number == 15
    assert wire_selection(mm(25), A).wire.number == 11
    assert wire_selection(mm("25.1"), A).wire.number == 10


def test_a_thicker_penetration_needs_a_coarser_wire():
    numbers = [wire_selection(mm(t), A).wire.number for t in (1, 5, 20, 50, 120)]
    assert numbers == sorted(numbers, reverse=True)


def test_the_double_wall_penalty_lands_bands_further_down():
    single = wire_selection(mm(10), A)
    double = wire_selection(mm(20), A)
    assert double.wire.number < single.wire.number
    assert double.wire.diameter > single.wire.diameter


def test_the_selection_says_which_stock_set_carries_the_wire():
    picked = wire_selection(mm(10), A)
    assert picked.available_sets == ("C", "D")
    assert "set C/D" in picked.describe()
    assert "class A" in picked.describe()


def test_the_finest_wire_in_class_b_is_only_in_the_last_set():
    finest = wire_selection(mm(1), B)
    assert finest.wire.number == 19
    assert finest.available_sets == ("D",)


def test_the_tables_run_out_at_different_thicknesses():
    assert table_ceiling(A) == mm(250)
    assert table_ceiling(B) == mm(350)
    with pytest.raises(TechniqueError) as caught:
        wire_selection(mm(300), A)
    assert caught.value.context["technique_class"] == "A"
    assert wire_selection(mm(300), B).wire.number == 6


def test_a_penetrated_thickness_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        wire_selection(Length.ZERO, A)
    with pytest.raises(ValidationError):
        wire_selection(Length(-1), B)


def test_both_tables_rise_in_thickness_and_fall_in_wire_number():
    for technique_class in (A, B):
        table = bands(technique_class)
        assert [t[0] for t in table] == sorted(t[0] for t in table)
        assert [t[1] for t in table] == sorted((t[1] for t in table), reverse=True)


def test_class_b_is_stricter_or_equal_everywhere_the_tables_overlap():
    for thickness in (1, 2, 5, 10, 20, 40, 80, 150, 200, 250):
        basic = wire_selection(mm(thickness), A)
        improved = wire_selection(mm(thickness), B)
        assert improved.wire.diameter <= basic.wire.diameter


def test_the_classes_print_the_way_a_specification_names_them():
    assert str(A) == "class A"
    assert str(B) == "class B"
    assert B.is_improved
    assert not A.is_improved
