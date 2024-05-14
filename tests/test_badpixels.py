from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.badpixels import (
    DEFAULT_POLICY,
    BadPixelMap,
    BadPixelPolicy,
)


def panel(pixels, columns=100, rows=100):
    return BadPixelMap(columns=columns, rows=rows, pixels=frozenset(pixels))


def test_scattered_single_pixels_are_a_thousand_clusters_of_one():
    scattered = {(c * 3, r * 3) for c in range(10) for r in range(10)}
    board = panel(scattered)
    assert board.total == 100
    clusters = board.clusters()
    assert len(clusters) == 100
    assert all(c.size == 1 for c in clusters)


def test_scattered_single_pixels_pass_however_many_there_are():
    scattered = {(c * 3, r * 3) for c in range(10) for r in range(10)}
    assert panel(scattered).passes()


def test_a_three_by_three_block_is_one_cluster_of_nine():
    block = {(c, r) for c in range(10, 13) for r in range(10, 13)}
    board = panel(block)
    assert len(board.clusters()) == 1
    biggest = board.largest_cluster()
    assert biggest.size == 9
    assert biggest.width == 3
    assert biggest.height == 3
    assert not biggest.is_line


def test_a_block_that_size_fails_the_default_policy():
    block = {(c, r) for c in range(10, 13) for r in range(10, 13)}
    violations = panel(block).violations()
    assert len(violations) == 1
    assert "a cluster of 9 pixels" in violations[0]


def test_diagonal_neighbours_count_as_connected():
    diagonal = {(5, 5), (6, 6), (7, 7)}
    board = panel(diagonal)
    assert len(board.clusters()) == 1
    assert board.largest_cluster().size == 3


def test_a_gap_of_two_breaks_the_connection():
    apart = {(5, 5), (8, 8)}
    assert len(panel(apart).clusters()) == 2


def test_a_dead_readout_line_is_recognised_as_a_line():
    line = {(20, r) for r in range(30, 45)}
    board = panel(line)
    found = board.lines()
    assert len(found) == 1
    assert found[0].length == 15
    assert found[0].width == 1
    assert found[0].is_line


def test_a_long_line_fails_where_a_compact_cluster_of_the_same_area_would_not():
    line = panel({(20, r) for r in range(30, 45)})
    assert not line.passes()
    assert "a bad line of 15 pixels" in line.violations()[0]

    compact = panel({(c, r) for c in range(10, 14) for r in range(10, 14)})
    loose = BadPixelPolicy(Decimal("0.02"), max_cluster=20, max_line=10)
    assert compact.passes(loose)


def test_a_short_line_inside_the_limit_passes():
    short = panel({(20, r) for r in range(30, 38)})
    assert short.lines()[0].length == 8
    assert short.passes()


def test_a_single_pixel_is_not_a_line():
    board = panel({(5, 5)})
    assert board.lines() == ()
    assert board.largest_cluster().size == 1
    assert not board.largest_cluster().is_line


def test_the_fraction_is_measured_against_the_whole_panel():
    board = panel({(c, 0) for c in range(50)}, columns=100, rows=100)
    assert board.fraction == Decimal("0.005")
    assert board.total == 50


def test_too_many_bad_pixels_fails_on_the_fraction_alone():
    many = {(c, r) for c in range(10) for r in range(0, 60, 2)}
    board = panel(many, columns=20, rows=60)
    assert board.fraction > Decimal("0.02")
    assert any("of the panel" in v for v in board.violations())


def test_clusters_come_back_largest_first():
    pixels = {(1, 1), (5, 5), (5, 6), (5, 7), (9, 9), (9, 10)}
    sizes = [c.size for c in panel(pixels).clusters()]
    assert sizes == [3, 2, 1]


def test_an_empty_map_is_clean():
    board = panel(set())
    assert board.total == 0
    assert board.clusters() == ()
    assert board.largest_cluster() is None
    assert board.passes()
    assert board.fraction == 0


def test_a_pixel_off_the_panel_is_refused():
    with pytest.raises(ValidationError) as caught:
        panel({(100, 5)})
    assert caught.value.context["column"] == 100
    with pytest.raises(ValidationError):
        panel({(-1, 5)})
    with pytest.raises(ValidationError):
        BadPixelMap(columns=0, rows=10, pixels=frozenset())


def test_a_policy_has_to_make_sense():
    with pytest.raises(ValidationError):
        BadPixelPolicy(Decimal("1.5"), 5, 10)
    with pytest.raises(ValidationError):
        BadPixelPolicy(Decimal("0.02"), 0, 10)
    assert DEFAULT_POLICY.max_cluster == 5
