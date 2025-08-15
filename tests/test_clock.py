from datetime import UTC, date, datetime, timedelta, timezone

import pytest

from qu_imager.clock import (
    at_offset,
    end_of,
    is_aware,
    on_day,
    require_aware,
    require_naive,
    same_kind,
    span,
    start_of,
    to_utc,
    utc_day,
    within,
)
from qu_imager.errors import ValidationError

AWARE = datetime(2025, 4, 14, 23, 30, tzinfo=UTC)
NAIVE = datetime(2025, 4, 14, 23, 30)
NEWFOUNDLAND = timezone(timedelta(hours=-3, minutes=-30))


def test_an_instant_knows_where_it_happened_and_wall_clock_does_not():
    assert is_aware(AWARE)
    assert not is_aware(NAIVE)


def test_an_instant_is_required_where_one_is_required():
    assert require_aware(AWARE) is AWARE
    with pytest.raises(ValidationError) as caught:
        require_aware(NAIVE, "assay")
    assert "assay" in caught.value.message


def test_wall_clock_is_required_where_that_is_required():
    assert require_naive(NAIVE) is NAIVE
    with pytest.raises(ValidationError):
        require_naive(AWARE, "permit window")


def test_comparing_across_the_two_kinds_is_refused():
    same_kind(AWARE, AWARE)
    same_kind(NAIVE, NAIVE)
    with pytest.raises(ValidationError) as caught:
        same_kind(AWARE, NAIVE)
    assert caught.value.context["left_aware"] is True


def test_an_instant_moves_into_universal_time_without_changing():
    local = AWARE.astimezone(NEWFOUNDLAND)
    assert to_utc(local) == AWARE
    assert to_utc(local).tzinfo is UTC


def test_wall_clock_time_cannot_be_moved_into_universal_time():
    with pytest.raises(ValidationError):
        to_utc(NAIVE)


def test_an_instant_can_be_read_at_a_stated_offset():
    read = at_offset(AWARE, -4)
    assert read.hour == 19
    assert read == AWARE


def test_an_offset_no_place_uses_is_refused():
    with pytest.raises(ValidationError):
        at_offset(AWARE, 20)
    with pytest.raises(ValidationError):
        at_offset(AWARE, -20)


def test_a_span_runs_forwards_between_two_of_a_kind():
    assert span(NAIVE, NAIVE + timedelta(hours=2)) == timedelta(hours=2)
    with pytest.raises(ValidationError):
        span(NAIVE + timedelta(hours=2), NAIVE)
    with pytest.raises(ValidationError):
        span(NAIVE, AWARE)


def test_the_local_day_and_the_universal_day_can_differ():
    just_past_midnight = datetime(2025, 4, 15, 0, 10, tzinfo=NEWFOUNDLAND)
    assert on_day(just_past_midnight) == date(2025, 4, 15)
    assert utc_day(just_past_midnight) == date(2025, 4, 15)
    earlier = datetime(2025, 4, 15, 0, 10, tzinfo=timezone(timedelta(hours=13)))
    assert utc_day(earlier) == date(2025, 4, 14)


def test_a_day_runs_from_midnight_to_the_last_moment_of_it():
    day = date(2025, 4, 14)
    assert start_of(day).hour == 0
    assert end_of(day).hour == 23
    assert start_of(day) < end_of(day)


def test_a_wall_clock_moment_falls_on_a_day_or_it_does_not():
    day = date(2025, 4, 14)
    assert within(NAIVE, day)
    assert not within(NAIVE + timedelta(days=1), day)
    assert within(start_of(day), day)
    assert within(end_of(day), day)


def test_an_instant_cannot_be_asked_which_wall_clock_day_it_is_on():
    with pytest.raises(ValidationError):
        within(AWARE, date(2025, 4, 14))


def test_the_decay_guard_is_the_shared_one_now():
    from qu_imager.quantities.activity import curie
    from qu_imager.sources.decay import activity_on
    from qu_imager.sources.isotope import IR192

    with pytest.raises(ValidationError) as caught:
        activity_on(curie(100), AWARE, IR192, NAIVE)
    assert caught.value.context["left_aware"] is True
    assert caught.value.context["right_aware"] is False


def test_a_site_window_refuses_an_instant_through_the_shared_guard():
    from qu_imager.scheduling import Window

    with pytest.raises(ValidationError) as caught:
        Window(AWARE, AWARE + timedelta(hours=2))
    assert "site window" in caught.value.message


def test_a_site_window_still_takes_wall_clock_time():
    from qu_imager.scheduling import Window

    window = Window(NAIVE, NAIVE + timedelta(hours=2))
    assert window.length.ms == 2 * 3600 * 1000


def test_the_day_slice_the_log_uses_is_the_shared_one():
    from qu_imager.services.daylog import shots_on

    assert callable(shots_on)
    day = date(2025, 4, 14)
    assert start_of(day) < end_of(day)
    assert within(datetime(2025, 4, 14, 12, 0), day)


def test_a_decay_between_two_instants_still_works():
    from qu_imager.quantities.activity import curie
    from qu_imager.sources.decay import activity_on
    from qu_imager.sources.isotope import IR192

    later = activity_on(curie(100), AWARE, IR192, AWARE + timedelta(days=74))
    assert later.curie < curie(100).curie


def test_every_module_that_takes_an_instant_says_so_the_same_way():
    from qu_imager.clock import require_aware

    for what in ("assay", "survey", "reading"):
        with pytest.raises(ValidationError) as caught:
            require_aware(NAIVE, what)
        assert what in caught.value.message
        assert caught.value.context["given"] == NAIVE.isoformat()


def test_every_module_that_takes_wall_clock_says_so_the_same_way():
    for what in ("site window", "permit", "shift"):
        with pytest.raises(ValidationError) as caught:
            require_naive(AWARE, what)
        assert what in caught.value.message


def test_an_offset_at_the_edge_of_the_range_is_allowed():
    assert at_offset(AWARE, 14).utcoffset() == timedelta(hours=14)
    assert at_offset(AWARE, -14).utcoffset() == timedelta(hours=-14)


def test_a_span_of_no_time_is_allowed():
    assert span(NAIVE, NAIVE) == timedelta(0)
    assert span(AWARE, AWARE) == timedelta(0)


def test_a_half_hour_zone_survives_the_round_trip():
    local = AWARE.astimezone(NEWFOUNDLAND)
    assert local.utcoffset() == timedelta(hours=-3, minutes=-30)
    assert to_utc(local) == AWARE


def test_the_universal_day_of_an_instant_does_not_move_with_the_zone_it_is_read_in():
    local = AWARE.astimezone(NEWFOUNDLAND)
    assert utc_day(local) == utc_day(AWARE)


def test_the_end_of_a_day_is_inside_it_and_the_next_midnight_is_not():
    day = date(2025, 4, 14)
    assert within(end_of(day), day)
    assert not within(start_of(date(2025, 4, 15)), day)


def test_midnight_matches_a_naive_moment():
    from datetime import date, datetime

    from qu_imager.clock import midnight_like

    at = midnight_like(date(2025, 8, 19), datetime(2024, 1, 1))
    assert at == datetime(2025, 8, 19, 0, 0)
    assert at.tzinfo is None


def test_midnight_matches_an_aware_moment():
    from datetime import UTC, date, datetime

    from qu_imager.clock import midnight_like

    at = midnight_like(date(2025, 8, 19), datetime(2024, 1, 1, tzinfo=UTC))
    assert at.tzinfo is UTC
    assert at.hour == 0


def test_midnight_carries_the_offset_it_was_given_not_utc():
    from datetime import date, datetime, timedelta, timezone

    from qu_imager.clock import midnight_like

    atlantic = timezone(timedelta(hours=-4))
    at = midnight_like(date(2025, 8, 19), datetime(2024, 1, 1, tzinfo=atlantic))
    assert at.utcoffset() == timedelta(hours=-4)


def test_a_midnight_matched_to_a_moment_can_always_be_compared_with_it():
    from datetime import UTC, date, datetime

    from qu_imager.clock import midnight_like

    for other in (datetime(2024, 1, 1), datetime(2024, 1, 1, tzinfo=UTC)):
        assert midnight_like(date(2025, 8, 19), other) > other
