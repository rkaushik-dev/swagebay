import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.indicators import HoleSize
from qu_imager.imaging.selection import (
    Family,
    IndicatorSide,
    TechniqueClass,
    band_ceiling,
    bands,
    class_b_is_stricter,
    hole_selection,
    is_boundary_thickness,
    select_for,
    table_bands,
    table_ceiling,
    wire_selection,
)
from qu_imager.quantities.length import Length, inch, mm

SOURCE = IndicatorSide.SOURCE
FILM = IndicatorSide.FILM


def test_a_half_inch_wall_takes_a_seventeen_on_the_source_side():
    picked = hole_selection(inch("0.50"), SOURCE)
    assert picked.indicator.identity == 17
    assert picked.hole is HoleSize.TWO_T


def test_the_film_side_column_is_one_row_easier():
    for thickness in ("0.20", "0.30", "0.60", "1.20", "3.00"):
        source = hole_selection(inch(thickness), SOURCE)
        film = hole_selection(inch(thickness), FILM)
        assert film.indicator.identity < source.indicator.identity


def test_a_band_is_closed_at_the_top_so_a_boundary_reads_down():
    assert hole_selection(inch("0.25"), SOURCE).indicator.identity == 12
    assert hole_selection(inch("0.2501"), SOURCE).indicator.identity == 15
    assert hole_selection(inch("0.375"), SOURCE).indicator.identity == 15
    assert hole_selection(inch("0.376"), SOURCE).indicator.identity == 17


def test_a_boundary_thickness_is_recognised_as_one():
    assert is_boundary_thickness(inch("0.25"))
    assert is_boundary_thickness(inch("2.00"))
    assert not is_boundary_thickness(inch("0.26"))


def test_the_band_ceiling_shows_the_working():
    assert band_ceiling(inch("0.30")) == inch("0.375")
    assert band_ceiling(inch("0.375")) == inch("0.375")
    assert band_ceiling(inch("18")) == inch("20.00")


def test_a_thin_wall_takes_the_smallest_plaque_in_the_table():
    assert hole_selection(mm(3), SOURCE).indicator.identity == 12
    assert hole_selection(mm(3), FILM).indicator.identity == 10
    assert hole_selection(Length(1), SOURCE).indicator.identity == 12


def test_the_table_runs_out_at_twenty_inches():
    with pytest.raises(TechniqueError) as caught:
        hole_selection(inch("20.01"), SOURCE)
    assert caught.value.context["table_ends_at_in"] == "20.00"
    with pytest.raises(TechniqueError):
        band_ceiling(inch(30))


def test_a_thickness_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        hole_selection(Length.ZERO, SOURCE)
    with pytest.raises(ValidationError):
        hole_selection(Length(-1), SOURCE)


def test_the_whole_table_rises_in_both_columns():
    bands = table_bands()
    assert [b[0] for b in bands] == sorted(b[0] for b in bands)
    assert [b[1] for b in bands] == sorted(b[1] for b in bands)
    assert [b[2] for b in bands] == sorted(b[2] for b in bands)
    assert len(bands) == 15


def test_the_film_side_column_is_the_source_column_shifted_by_one():
    bands = table_bands()
    for earlier, later in zip(bands, bands[1:], strict=False):
        assert later[2] == earlier[1]


def test_a_film_side_indicator_has_to_be_marked():
    assert hole_selection(inch("0.50"), FILM).needs_f_marker
    assert not hole_selection(inch("0.50"), SOURCE).needs_f_marker
    assert FILM.needs_marking
    assert not SOURCE.needs_marking


def test_the_selection_prints_the_way_the_report_reads():
    assert hole_selection(inch("0.50"), SOURCE).describe() == "17 2T on the source side"
    assert (
        hole_selection(inch("0.50"), FILM).describe() == "15 2T on the film side with an F marker"
    )


def test_the_essential_hole_is_two_t_all_the_way_down():
    for ceiling, _source, _film in table_bands():
        assert hole_selection(ceiling, SOURCE).hole is HoleSize.TWO_T
        assert hole_selection(ceiling, FILM).hole is HoleSize.TWO_T


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


def test_the_family_decides_which_table_is_read():
    hole = select_for(Family.HOLE, inch("0.50"), mm(26))
    wire = select_for(Family.WIRE, inch("0.50"), mm(26))
    assert hole.indicator.identity == 17
    assert wire.wire.number == 10


def test_the_wire_family_reads_the_penetrated_thickness_and_the_hole_family_the_wall():
    assert Family.WIRE.measured_on_penetrated_thickness
    assert not Family.HOLE.measured_on_penetrated_thickness
    single = select_for(Family.WIRE, inch("0.50"), mm(13))
    double = select_for(Family.WIRE, inch("0.50"), mm(26))
    assert single.wire.number > double.wire.number


def test_the_hole_family_ignores_the_penetrated_thickness_entirely():
    near = select_for(Family.HOLE, inch("0.50"), mm(13))
    far = select_for(Family.HOLE, inch("0.50"), mm(90))
    assert near.indicator.identity == far.indicator.identity


def test_the_entry_point_still_takes_a_side_and_a_class():
    marked = select_for(Family.HOLE, inch("0.50"), mm(26), side=IndicatorSide.FILM)
    assert marked.needs_f_marker
    strict = select_for(Family.WIRE, inch("0.50"), mm(26), technique_class=TechniqueClass.B)
    assert strict.wire.number > wire_selection(mm(26), TechniqueClass.A).wire.number


def test_the_family_prints_the_way_a_specification_names_it():
    assert str(Family.HOLE) == "hole type"
    assert str(Family.WIRE) == "wire type"
