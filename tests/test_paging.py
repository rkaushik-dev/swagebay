import pytest

from qu_imager.api.paging import (
    DEFAULT_PAGE,
    MAX_PAGE,
    after,
    clamp,
    fetch_size,
    paginate,
)
from qu_imager.errors import ValidationError


def rows(count, start=1):
    return [f"R-{i:04d}" for i in range(start, start + count)]


def test_a_short_list_is_the_last_page():
    page = paginate(rows(10), key=str, size=50)
    assert page.count == 10
    assert page.is_last
    assert page.next_cursor is None


def test_a_full_list_carries_a_cursor_to_the_next_page():
    page = paginate(rows(51), key=str, size=50)
    assert page.count == 50
    assert not page.is_last
    assert page.next_cursor == "R-0050"


def test_the_cursor_is_the_last_row_the_client_saw():
    page = paginate(rows(6), key=str, size=5)
    assert page.next_cursor == page.items[-1]


def test_a_list_exactly_the_page_size_is_the_last_page():
    page = paginate(rows(50), key=str, size=50)
    assert page.is_last


def test_an_empty_list_is_a_last_page_with_nothing_in_it():
    page = paginate([], key=str, size=50)
    assert page.count == 0
    assert page.is_last


def test_the_default_page_size_is_used_when_none_is_asked_for():
    assert clamp(None) == DEFAULT_PAGE
    assert paginate(rows(100), key=str).count == DEFAULT_PAGE


def test_a_page_size_over_the_ceiling_is_clamped_rather_than_refused():
    assert clamp(10_000) == MAX_PAGE
    assert paginate(rows(1000), key=str, size=10_000).size == MAX_PAGE


def test_a_page_size_below_one_is_refused():
    with pytest.raises(ValidationError):
        clamp(0)
    with pytest.raises(ValidationError):
        clamp(-5)


def test_the_fetch_size_is_one_more_than_the_page():
    assert fetch_size(50) == 51
    assert fetch_size(None) == DEFAULT_PAGE + 1
    assert fetch_size(10_000) == MAX_PAGE + 1


def test_a_cursor_walks_the_whole_list_without_repeating_a_row():
    everything = rows(23)
    seen: list[str] = []
    cursor = None
    while True:
        remaining = [r for r in everything if cursor is None or r > cursor]
        page = paginate(remaining, key=str, size=5)
        seen.extend(page.items)
        if page.is_last:
            break
        cursor = page.next_cursor
    assert seen == everything
    assert len(set(seen)) == len(seen)


def test_a_row_inserted_behind_the_cursor_does_not_shift_the_page():
    first = paginate(rows(10), key=str, size=5)
    cursor = first.next_cursor
    with_insert = sorted([*rows(10), "R-0002a"])
    remaining = [r for r in with_insert if r > cursor]
    second = paginate(remaining, key=str, size=5)
    assert not set(first.items) & set(second.items)


def test_a_blank_cursor_reads_as_no_cursor():
    assert after(None) is None
    assert after("") is None
    assert after("   ") is None
    assert after(" R-0005 ") == "R-0005"


def test_a_key_that_is_not_callable_is_refused():
    with pytest.raises(ValidationError):
        paginate(rows(3), key="not a function", size=2)


def test_the_page_reports_the_size_it_was_cut_to():
    assert paginate(rows(3), key=str, size=2).size == 2
    assert paginate(rows(3), key=str).size == DEFAULT_PAGE
