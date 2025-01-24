from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.geometry.weldmap import Arc, Map, even_arcs, wrap

DEGREE = chr(176)


def arc(label="1", start=0, span=120):
    return Arc(label=label, starts_at=Decimal(start), span=Decimal(span))


def test_bearings_fold_into_one_turn():
    assert wrap(Decimal(370)) == Decimal(10)
    assert wrap(Decimal(-10)) == Decimal(350)
    assert wrap(Decimal(360)) == Decimal(0)


def test_an_arc_knows_where_it_ends():
    assert arc(start=0, span=120).ends_at == Decimal(120)
    assert arc(start=300, span=120).ends_at == Decimal(60)


def test_an_arc_that_crosses_twelve_says_so():
    assert arc(start=330, span=60).wraps
    assert not arc(start=0, span=120).wraps


def test_a_wrapping_arc_is_one_arc_and_not_two():
    crossing = arc(start=330, span=60)
    assert crossing.contains(Decimal(340))
    assert crossing.contains(Decimal(0))
    assert crossing.contains(Decimal(20))
    assert not crossing.contains(Decimal(100))


def test_an_arc_is_closed_at_the_start_and_open_at_the_end():
    span = arc(start=0, span=120)
    assert span.contains(Decimal(0))
    assert span.contains(Decimal("119.9"))
    assert not span.contains(Decimal(120))


def test_an_arc_with_no_span_or_no_label_is_refused():
    with pytest.raises(ValidationError):
        arc(span=0)
    with pytest.raises(ValidationError):
        arc(label="  ")
    with pytest.raises(ValidationError):
        arc(span=400)


def test_three_even_films_close_the_circle():
    weld = even_arcs(3)
    assert weld.is_complete
    assert weld.gaps() == ()


def test_films_that_do_not_reach_leave_a_gap_that_is_found():
    weld = Map((arc("1", 0, 100), arc("2", 120, 100), arc("3", 240, 100)))
    gaps = weld.gaps()
    assert len(gaps) == 3
    assert gaps[0].starts_at == Decimal(100)


def test_a_gap_that_crosses_twelve_is_reported_as_one_gap():
    weld = Map((arc("1", 20, 300),))
    gaps = weld.gaps()
    assert len(gaps) == 1
    assert gaps[0].starts_at == Decimal(320)
    assert gaps[0].span == Decimal(60)
    assert gaps[0].describe() == "320.0" + DEGREE + " to 20.0" + DEGREE


def test_a_single_film_round_the_whole_weld_leaves_nothing():
    weld = Map((arc("1", 0, 360),))
    assert weld.is_complete


def test_the_map_says_which_films_cover_a_bearing():
    weld = even_arcs(3, overlap_degrees=Decimal(20))
    assert weld.covers(Decimal(60)) == ("1",)
    assert len(weld.covers(Decimal(125))) == 2


def test_a_bearing_nothing_reaches_comes_back_empty():
    weld = Map((arc("1", 0, 100),))
    assert weld.covers(Decimal(200)) == ()


def test_the_overlap_between_two_films_is_measured_by_walking():
    left = arc("1", 0, 130)
    right = arc("2", 120, 130)
    assert left.overlap_with(right) == Decimal(10)


def test_an_overlap_that_crosses_twelve_is_measured_too():
    left = arc("1", 300, 120)
    right = arc("2", 350, 60)
    assert left.overlap_with(right) > 0


def test_two_films_that_do_not_touch_overlap_by_nothing():
    assert arc("1", 0, 100).overlap_with(arc("2", 200, 100)) == Decimal(0)


def test_the_stretches_only_one_film_reaches_are_reported():
    weld = even_arcs(3, overlap_degrees=Decimal(20))
    thin = weld.single_covered()
    assert thin
    assert all(g.span > 0 for g in thin)


def test_a_weld_with_no_overlap_anywhere_is_entirely_single_covered():
    weld = even_arcs(4, overlap_degrees=Decimal(0))
    assert weld.is_complete
    assert len(weld.single_covered()) == 1
    assert weld.single_covered()[0].span == Decimal(360)


def test_a_layout_needs_at_least_one_film():
    with pytest.raises(ValidationError):
        even_arcs(0)
    with pytest.raises(ValidationError):
        even_arcs(3, overlap_degrees=Decimal(-1))


def test_a_walk_step_that_is_not_small_is_refused():
    with pytest.raises(ValidationError):
        even_arcs(3).gaps(step=Decimal(0))
    with pytest.raises(ValidationError):
        even_arcs(3).gaps(step=Decimal(30))


def test_an_arc_prints_the_way_the_sketch_is_labelled():
    assert arc("2", 120, 130).describe() == "2 120\u00b0 for 130\u00b0"
