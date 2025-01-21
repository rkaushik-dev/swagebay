from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.stock import SHORT_DATED_DAYS, Lot, Store, shelf_life

TODAY = date(2025, 1, 20)


def lot(number="L-1", sheets=100, expires_in=200, film="d7", size="4x10"):
    return Lot(
        lot_number=number,
        film_key=film,
        size=size,
        sheets=sheets,
        received_on=TODAY - timedelta(days=30),
        expires_on=TODAY + timedelta(days=expires_in),
    )


def stocked(*lots):
    store = Store()
    for item in lots:
        store.receive(item)
    return store


def test_film_comes_off_the_shelf_shortest_dated_first():
    store = stocked(lot("L-1", expires_in=300), lot("L-2", expires_in=100))
    assert [item.lot_number for item in store.usable_on(TODAY, "d7", "4x10")] == ["L-2", "L-1"]


def test_the_issue_order_is_by_expiry_and_not_by_receipt():
    old = lot("L-1", expires_in=300)
    short = lot("L-2", expires_in=50)
    store = stocked(old, short)
    assert store.issue(TODAY, "d7", "4x10", 10) == (("L-2", 10),)


def test_an_issue_spans_lots_when_one_will_not_cover_it():
    store = stocked(lot("L-1", sheets=30, expires_in=50), lot("L-2", sheets=100, expires_in=300))
    assert store.issue(TODAY, "d7", "4x10", 50) == (("L-1", 30), ("L-2", 20))


def test_issuing_takes_the_sheets_off_the_shelf():
    store = stocked(lot("L-1", sheets=100))
    store.issue(TODAY, "d7", "4x10", 40)
    assert store.on_hand(TODAY, "d7", "4x10") == 60


def test_expired_film_is_never_issued():
    store = stocked(lot("L-1", expires_in=-1), lot("L-2", expires_in=100))
    assert [item.lot_number for item in store.usable_on(TODAY, "d7", "4x10")] == ["L-2"]
    assert store.on_hand(TODAY, "d7", "4x10") == 100


def test_asking_for_more_than_there_is_names_the_shortfall():
    store = stocked(lot("L-1", sheets=20))
    with pytest.raises(ConflictError) as caught:
        store.issue(TODAY, "d7", "4x10", 50)
    assert caught.value.context["on_hand"] == 20
    assert caught.value.context["wanted"] == 50


def test_a_failed_issue_takes_nothing_off_the_shelf():
    store = stocked(lot("L-1", sheets=20))
    with pytest.raises(ConflictError):
        store.issue(TODAY, "d7", "4x10", 50)
    assert store.on_hand(TODAY, "d7", "4x10") == 20


def test_an_issue_of_nothing_is_refused():
    store = stocked(lot())
    with pytest.raises(ValidationError):
        store.issue(TODAY, "d7", "4x10", 0)


def test_film_of_another_size_or_type_is_not_on_hand():
    store = stocked(lot("L-1", size="4x10"), lot("L-2", size="7x17"), lot("L-3", film="d4"))
    assert store.on_hand(TODAY, "d7", "4x10") == 100
    assert store.on_hand(TODAY, "d7", "7x17") == 100
    assert store.on_hand(TODAY, "d5", "4x10") == 0


def test_the_expired_list_only_names_lots_with_film_left_in_them():
    store = stocked(lot("L-1", expires_in=-1), lot("L-2", sheets=0, expires_in=-1))
    assert store.expired_on(TODAY) == ("L-1",)


def test_the_short_dated_list_is_what_gets_used_first():
    store = stocked(lot("L-1", expires_in=30), lot("L-2", expires_in=300))
    assert store.short_dated_on(TODAY) == ("L-1",)
    assert SHORT_DATED_DAYS == 60


def test_something_already_expired_is_not_short_dated():
    store = stocked(lot("L-1", expires_in=-1))
    assert store.short_dated_on(TODAY) == ()
    assert store.expired_on(TODAY) == ("L-1",)


def test_the_days_left_run_down_and_go_negative():
    assert lot(expires_in=30).days_left_on(TODAY) == 30
    assert lot(expires_in=-5).days_left_on(TODAY) == -5


def test_the_same_lot_twice_is_refused():
    store = stocked(lot("L-1"))
    with pytest.raises(ConflictError):
        store.receive(lot("L-1"))


def test_a_lot_needs_a_number_and_a_date_that_makes_sense():
    with pytest.raises(ValidationError):
        lot(number="  ")
    with pytest.raises(ValidationError):
        lot(sheets=-1)
    with pytest.raises(ValidationError):
        Lot("L-1", "d7", "4x10", 100, TODAY, TODAY)


def test_the_reorder_point_fires_at_the_floor_and_not_below_it():
    store = stocked(lot("L-1", sheets=25))
    assert not store.reorder_at(TODAY, "d7", "4x10", 20)
    assert store.reorder_at(TODAY, "d7", "4x10", 25)
    with pytest.raises(ValidationError):
        store.reorder_at(TODAY, "d7", "4x10", -1)


def test_the_default_shelf_life_is_eighteen_months():
    assert shelf_life(TODAY) == TODAY + timedelta(days=540)
    assert shelf_life(TODAY, months=6) == TODAY + timedelta(days=180)
    with pytest.raises(ValidationError):
        shelf_life(TODAY, months=0)


def test_a_lot_prints_the_way_the_shelf_label_reads():
    assert lot().describe() == "L-1 d7 4x10, 100 sheets"
