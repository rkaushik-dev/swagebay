import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.imaging.markers import (
    MINIMUM_OVERLAP,
    FilmSpan,
    Marker,
    coverage_gaps,
    effective_coverage,
    films_required,
    missing_markers,
    plan_films,
    plan_markers,
)
from qu_imager.quantities.length import Length, mm


def test_the_overlap_is_given_back_out_of_every_film():
    assert effective_coverage(mm(350)) == mm(325)
    assert effective_coverage(mm(350), mm(50)) == mm(300)
    assert mm(25) == MINIMUM_OVERLAP


def test_an_overlap_that_swallows_the_film_is_refused():
    with pytest.raises(TechniqueError) as caught:
        effective_coverage(mm(100), mm(100))
    assert caught.value.context["film_mm"] == "100"
    with pytest.raises(TechniqueError):
        effective_coverage(mm(100), mm(150))


def test_a_film_and_an_overlap_both_need_real_lengths():
    with pytest.raises(ValidationError):
        effective_coverage(Length.ZERO)
    with pytest.raises(ValidationError):
        effective_coverage(mm(350), Length(-1))


def test_a_short_weld_takes_one_film():
    assert films_required(mm(300), mm(350)) == 1
    assert films_required(mm(350), mm(350)) == 1


def test_a_long_weld_takes_more_than_the_raw_division_suggests():
    assert films_required(mm(1000), mm(350)) == 3
    assert films_required(mm(1000), mm(350), mm(100)) == 4
    assert mm(1000).ratio(mm(350)) < 3


def test_a_weld_needs_a_length_to_be_planned():
    with pytest.raises(ValidationError):
        films_required(Length.ZERO, mm(350))


def test_the_plan_lays_the_films_end_to_end_with_the_overlap():
    spans = plan_films(mm(1000), mm(350))
    assert len(spans) == 3
    assert spans[0].starts_at == Length.ZERO
    assert spans[0].ends_at == mm(350)
    assert spans[1].starts_at == mm(325)
    assert spans[2].ends_at == mm(1000)


def test_the_last_film_is_pulled_back_so_it_does_not_run_off_the_end():
    spans = plan_films(mm(1000), mm(350))
    last = spans[-1]
    assert last.ends_at == mm(1000)
    assert last.length == mm(350)


def test_consecutive_films_really_do_overlap():
    spans = plan_films(mm(1000), mm(350))
    for earlier, later in zip(spans, spans[1:], strict=False):
        assert later.starts_at < earlier.ends_at


def test_a_single_film_plan_starts_at_zero_and_ends_at_the_weld():
    spans = plan_films(mm(300), mm(350))
    assert len(spans) == 1
    assert spans[0].starts_at == Length.ZERO
    assert spans[0].ends_at == mm(300)


def test_a_marker_goes_at_the_start_of_every_film_and_one_at_the_far_end():
    spans = plan_films(mm(1000), mm(350))
    markers = plan_markers(spans)
    assert [m.label for m in markers] == ["1", "2", "3", "4"]
    assert markers[0].at == Length.ZERO
    assert markers[-1].at == mm(1000)


def test_markers_can_be_asked_which_film_they_land_on():
    spans = plan_films(mm(1000), mm(350))
    markers = plan_markers(spans)
    on_first = spans[0].markers_within(markers)
    assert [m.label for m in on_first] == ["1", "2"]


def test_a_marker_that_did_not_show_up_is_the_finding():
    spans = plan_films(mm(1000), mm(350))
    markers = plan_markers(spans)
    absent = missing_markers(markers, frozenset({"1", "2", "4"}))
    assert [m.label for m in absent] == ["3"]
    assert missing_markers(markers, frozenset({"1", "2", "3", "4"})) == ()


def test_a_sound_plan_leaves_no_gaps():
    spans = plan_films(mm(1000), mm(350))
    assert coverage_gaps(spans, mm(1000)) == ()


def test_a_gap_in_the_middle_is_found_and_measured():
    spans = (
        FilmSpan(1, Length.ZERO, mm(300)),
        FilmSpan(2, mm(400), mm(700)),
    )
    gaps = coverage_gaps(spans, mm(700))
    assert gaps == ("a gap from 300 mm to 400 mm",)


def test_a_gap_at_either_end_is_found_too():
    spans = (FilmSpan(1, mm(50), mm(300)),)
    gaps = coverage_gaps(spans, mm(700))
    assert gaps[0] == "nothing covers the first 50 mm"
    assert gaps[1] == "nothing covers past 300 mm"


def test_no_films_at_all_is_reported_as_such():
    assert coverage_gaps((), mm(700)) == ("no films are planned",)
    with pytest.raises(ValidationError):
        plan_markers(())


def test_a_film_span_and_a_marker_both_need_to_make_sense():
    with pytest.raises(ValidationError):
        FilmSpan(1, mm(300), mm(300))
    with pytest.raises(ValidationError):
        Marker("  ", mm(10))
    with pytest.raises(ValidationError):
        Marker("1", Length(-1))
    assert str(Marker("3", mm(650))) == "3 at 650 mm"
