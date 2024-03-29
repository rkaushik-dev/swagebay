from datetime import date, datetime, time

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import Duration, minutes
from qu_imager.scheduling import (
    SiteSchedule,
    Window,
    WindowKind,
    night_window,
)

MONDAY = date(2024, 4, 8)


def test_a_night_window_runs_over_midnight():
    window = night_window(MONDAY)
    assert window.starts_at == datetime(2024, 4, 8, 22, 0)
    assert window.ends_at == datetime(2024, 4, 9, 5, 30)
    assert window.length == minutes(450)


def test_a_window_that_ends_before_it_starts_is_refused():
    with pytest.raises(ValidationError):
        Window(datetime(2024, 4, 8, 22, 0), datetime(2024, 4, 8, 21, 0))
    with pytest.raises(ValidationError):
        Window(datetime(2024, 4, 8, 22, 0), datetime(2024, 4, 8, 22, 0))


def test_an_aware_timestamp_is_refused_because_permits_are_wall_clock():
    from datetime import UTC

    with pytest.raises(ValidationError):
        Window(
            datetime(2024, 4, 8, 22, 0, tzinfo=UTC),
            datetime(2024, 4, 9, 5, 30, tzinfo=UTC),
        )


def test_containment_is_half_open_at_the_close():
    window = night_window(MONDAY)
    assert window.contains(datetime(2024, 4, 8, 22, 0))
    assert window.contains(datetime(2024, 4, 9, 5, 29))
    assert not window.contains(datetime(2024, 4, 9, 5, 30))
    assert not window.contains(datetime(2024, 4, 8, 21, 59))


def test_time_remaining_runs_down_and_stops_at_zero():
    window = night_window(MONDAY)
    assert window.remaining_at(datetime(2024, 4, 9, 5, 0)) == minutes(30)
    assert window.remaining_at(datetime(2024, 4, 9, 6, 0)) == Duration.ZERO
    assert window.remaining_at(datetime(2024, 4, 8, 12, 0)) == minutes(450)


def test_a_job_fits_only_if_the_time_left_covers_it():
    window = night_window(MONDAY)
    late = datetime(2024, 4, 9, 5, 0)
    assert window.fits(minutes(30), late)
    assert not window.fits(minutes(31), late)


def test_a_schedule_keeps_its_windows_in_order():
    later = night_window(date(2024, 4, 10))
    earlier = night_window(MONDAY)
    schedule = SiteSchedule([later, earlier])
    assert schedule.windows == [earlier, later]


def test_overlapping_grants_are_refused_at_construction():
    first = Window(datetime(2024, 4, 8, 22, 0), datetime(2024, 4, 9, 6, 0))
    second = Window(datetime(2024, 4, 9, 5, 0), datetime(2024, 4, 9, 8, 0))
    assert first.overlaps(second)
    with pytest.raises(ValidationError):
        SiteSchedule([first, second])


def test_touching_windows_do_not_overlap():
    first = Window(datetime(2024, 4, 8, 22, 0), datetime(2024, 4, 9, 6, 0))
    second = Window(datetime(2024, 4, 9, 6, 0), datetime(2024, 4, 9, 8, 0))
    assert not first.overlaps(second)
    assert len(SiteSchedule([first, second]).windows) == 2


def test_the_schedule_answers_whether_the_area_is_clear_right_now():
    schedule = SiteSchedule([night_window(MONDAY), night_window(date(2024, 4, 10))])
    assert schedule.is_open_at(datetime(2024, 4, 9, 1, 0))
    assert not schedule.is_open_at(datetime(2024, 4, 9, 12, 0))
    assert schedule.window_at(datetime(2024, 4, 9, 1, 0)).kind is WindowKind.NIGHT


def test_the_next_window_is_the_next_one_that_has_not_started():
    schedule = SiteSchedule([night_window(MONDAY), night_window(date(2024, 4, 10))])
    following = schedule.next_window_after(datetime(2024, 4, 9, 1, 0))
    assert following.starts_at == datetime(2024, 4, 10, 22, 0)
    assert schedule.next_window_after(datetime(2024, 4, 20)) is None


def test_a_long_job_skips_a_window_that_is_too_short_for_it():
    short = Window(datetime(2024, 4, 8, 22, 0), datetime(2024, 4, 8, 23, 0))
    long = night_window(date(2024, 4, 10))
    schedule = SiteSchedule([short, long])
    found = schedule.first_window_fitting(minutes(120), datetime(2024, 4, 8, 12, 0))
    assert found == long
    assert schedule.first_window_fitting(minutes(30), datetime(2024, 4, 8, 12, 0)) == short


def test_a_job_that_fits_nowhere_comes_back_empty():
    schedule = SiteSchedule([night_window(MONDAY)])
    assert schedule.first_window_fitting(Duration.from_hours(20), datetime(2024, 4, 1)) is None


def test_the_open_time_on_a_day_counts_only_that_day_s_share():
    schedule = SiteSchedule([night_window(MONDAY)])
    assert schedule.total_open(MONDAY) == minutes(120)
    assert schedule.total_open(date(2024, 4, 9)) == minutes(330)
    assert schedule.total_open(date(2024, 4, 11)) == Duration.ZERO


def test_shutdowns_can_be_extended_and_nights_cannot():
    assert WindowKind.SHUTDOWN.is_extendable
    assert WindowKind.WEEKEND.is_extendable
    assert not WindowKind.NIGHT.is_extendable
    assert not WindowKind.PERMIT.is_extendable


def test_a_window_prints_the_way_a_permit_reads():
    window = Window(
        datetime(2024, 4, 8, 22, 0),
        datetime(2024, 4, 9, 5, 30),
        WindowKind.PERMIT,
    )
    assert str(window) == "permit 2024-04-08 22:00 to 05:30"
    assert night_window(MONDAY, opens=time(20, 0)).starts_at.hour == 20
