"""Properties of the geometry and the aggregate window.

The window rule is the one worth generating cases for. It is a maximisation
over a continuum of window positions reduced to a finite candidate list, and a
reduction like that is either exactly right or subtly wrong in a way no
handful of examples finds. The property that matters is that no window position
anybody can name beats the one the search returned.

The unsharpness properties are cheaper but they caught a real defect: the
minimum distance helper rounded with truncating decimal division and came back
one micrometre short, so a distance computed to pass the check failed it.
"""

from decimal import Decimal

from hypothesis import given, settings
from hypothesis import strategies as st

from qu_imager.evaluation.aggregate import (
    STANDARD_WINDOW,
    aggregate_in,
    clipped_length,
    worst_window,
)
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry.beam import (
    BeamGeometry,
    minimum_source_to_object,
    unsharpness_limit,
)
from qu_imager.geometry.weldmap import Arc, Map, wrap
from qu_imager.quantities.length import Length

lengths = st.integers(min_value=1000, max_value=2_000_000).map(Length)
focal = st.integers(min_value=100, max_value=6000).map(Length)
gaps = st.integers(min_value=0, max_value=500_000).map(Length)
bearings = st.integers(min_value=0, max_value=3599).map(lambda t: Decimal(t) / 10)


@given(lengths, gaps, focal)
def test_the_three_distances_always_add_up(sod, ofd, spot):
    beam = BeamGeometry(sod, ofd, spot)
    assert beam.source_to_film == sod + ofd


@given(lengths, gaps, focal)
def test_building_from_the_tape_measurement_is_the_inverse(sod, ofd, spot):
    beam = BeamGeometry(sod, ofd, spot)
    rebuilt = BeamGeometry.from_sfd(beam.source_to_film, ofd, spot)
    assert rebuilt == beam


@given(lengths, gaps, focal)
def test_the_magnification_is_never_below_one(sod, ofd, spot):
    assert BeamGeometry(sod, ofd, spot).magnification >= Decimal(1)


@given(lengths, gaps, focal)
def test_moving_the_source_back_never_increases_the_blur(sod, ofd, spot):
    near = BeamGeometry(sod, ofd, spot)
    far = BeamGeometry(Length(sod.um * 2), ofd, spot)
    assert far.geometric_unsharpness <= near.geometric_unsharpness


@given(focal, gaps, lengths)
def test_the_minimum_distance_really_does_meet_the_limit(spot, ofd, wall):
    limit = unsharpness_limit(wall)
    closest = minimum_source_to_object(spot, ofd, limit)
    assert BeamGeometry(closest, ofd, spot).geometric_unsharpness <= limit


@given(bearings)
def test_wrapping_a_bearing_lands_inside_one_turn(bearing):
    folded = wrap(bearing)
    assert Decimal(0) <= folded < Decimal(360)


@given(bearings, st.integers(min_value=1, max_value=359))
def test_an_arc_contains_its_own_start_and_not_its_own_end(start, span):
    arc = Arc("a", wrap(start), Decimal(span))
    assert arc.contains(arc.starts_at)
    assert not arc.contains(arc.ends_at)


@given(st.integers(min_value=1, max_value=12))
def test_evenly_spaced_arcs_close_the_circle(count):
    from qu_imager.geometry.weldmap import even_arcs

    assert even_arcs(count).is_complete


@given(bearings)
def test_a_single_full_arc_covers_every_bearing(bearing):
    weld = Map((Arc("1", Decimal(0), Decimal(360)),))
    assert weld.covers(bearing) == ("1",)


indication_positions = st.integers(min_value=0, max_value=900_000)
indication_lengths = st.integers(min_value=1000, max_value=120_000)


@st.composite
def indications(draw, weld_um: int):
    count = draw(st.integers(min_value=0, max_value=6))
    made = []
    for _ in range(count):
        start = draw(st.integers(min_value=0, max_value=max(weld_um - 2000, 1)))
        span = draw(st.integers(min_value=1000, max_value=min(120_000, weld_um - start)))
        made.append(Indication(Kind.ESI, Length(start), Length(span), Length(1000)))
    return tuple(made)


@settings(max_examples=60)
@given(
    st.integers(min_value=400_000, max_value=2_000_000).flatmap(
        lambda weld: st.tuples(st.just(weld), indications(weld))
    )
)
def test_no_window_position_beats_the_one_the_search_found(case):
    weld_um, found = case
    weld = Length(weld_um)
    best = worst_window(found, weld)
    span = min(STANDARD_WINDOW, weld)
    limit = weld.um - span.um
    for start_um in range(0, max(limit, 0) + 1, 25_000):
        total, _count = aggregate_in(found, Length(start_um), Length(start_um + span.um))
        assert total <= best.aggregate


@settings(max_examples=60)
@given(
    st.integers(min_value=400_000, max_value=2_000_000).flatmap(
        lambda weld: st.tuples(st.just(weld), indications(weld))
    )
)
def test_the_worst_window_never_counts_more_than_its_own_length(case):
    weld_um, found = case
    weld = Length(weld_um)
    best = worst_window(found, weld)
    assert best.aggregate <= best.length


@settings(max_examples=60)
@given(
    st.integers(min_value=400_000, max_value=2_000_000).flatmap(
        lambda weld: st.tuples(st.just(weld), indications(weld))
    )
)
def test_the_worst_window_stays_on_the_weld(case):
    weld_um, found = case
    weld = Length(weld_um)
    best = worst_window(found, weld)
    assert best.starts_at.um >= 0
    assert best.ends_at <= weld


@given(indication_positions, indication_lengths, indication_positions)
def test_clipping_never_returns_more_than_the_indication_itself(start, span, window_start):
    found = Indication(Kind.ESI, Length(start), Length(span), Length(1000))
    inside = clipped_length(found, Length(window_start), Length(window_start + 300_000))
    assert inside <= found.length


@given(indication_positions, indication_lengths)
def test_a_window_that_swallows_an_indication_clips_to_the_whole_of_it(start, span):
    found = Indication(Kind.ESI, Length(start), Length(span), Length(1000))
    inside = clipped_length(found, Length(0), Length(start + span + 1))
    assert inside == found.length
