import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.indicators import HoleSize
from qu_imager.imaging.selection import (
    IndicatorSide,
    band_ceiling,
    hole_selection,
    is_boundary_thickness,
    table_bands,
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
