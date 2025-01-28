from datetime import date, datetime

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import Duration, minutes
from qu_imager.scheduling import SiteSchedule, Window, night_window
from qu_imager.services.shifts import (
    DEFAULT_SETUP,
    Piece,
    plan_shift,
    spare_in,
    window_utilisation,
)

MONDAY = date(2025, 1, 27)


def schedule(*days):
    return SiteSchedule([night_window(day) for day in (days or (MONDAY,))])


def work(*lengths, prefix="GW"):
    return tuple(
        Piece(weld=f"{prefix}-{i + 100}", source_out=minutes(m)) for i, m in enumerate(lengths)
    )


def test_a_night_that_fits_everything_leaves_nothing_over():
    plan = plan_shift(schedule(), work(30, 30, 30))
    assert plan.is_complete
    assert len(plan.slots) == 3
    assert plan.windows_used == 1


def test_the_joints_are_placed_in_the_order_they_were_given():
    plan = plan_shift(schedule(), work(30, 30, 30))
    assert [slot.weld for slot in plan.slots] == ["GW-100", "GW-101", "GW-102"]


def test_the_first_joint_starts_after_the_setup_and_not_at_the_window():
    plan = plan_shift(schedule(), work(30))
    first = plan.slots[0]
    assert first.starts_at == datetime(2025, 1, 27, 22, 40)
    assert first.window_starts_at == datetime(2025, 1, 27, 22, 0)


def test_setup_is_charged_once_a_window_and_not_once_a_joint():
    one = plan_shift(schedule(), work(400))
    many = plan_shift(schedule(), work(*([50] * 8)))
    assert one.is_complete
    assert many.is_complete


def test_work_that_does_not_fit_rolls_over_and_is_named():
    plan = plan_shift(schedule(), work(*([120] * 5)))
    assert not plan.is_complete
    assert plan.left_over
    assert plan.left_over[0] not in [slot.weld for slot in plan.slots]


def test_the_left_over_work_goes_into_the_next_night():
    plan = plan_shift(schedule(MONDAY, date(2025, 1, 28)), work(*([120] * 5)))
    assert plan.is_complete
    assert plan.windows_used == 2


def test_a_joint_longer_than_any_window_never_fits():
    plan = plan_shift(schedule(), work(600))
    assert plan.slots == ()
    assert plan.left_over == ("GW-100",)


def test_a_window_too_short_for_the_setup_is_skipped_entirely():
    tight = Window(datetime(2025, 1, 27, 22, 0), datetime(2025, 1, 27, 22, 30))
    plan = plan_shift(SiteSchedule([tight]), work(10))
    assert plan.windows_used == 0
    assert plan.left_over == ("GW-100",)


def test_the_joints_do_not_overlap_each_other():
    plan = plan_shift(schedule(), work(30, 30, 30))
    for earlier, later in zip(plan.slots, plan.slots[1:], strict=False):
        assert later.starts_at >= earlier.ends_at


def test_every_joint_stays_inside_its_window():
    windows = schedule().windows
    plan = plan_shift(schedule(), work(60, 60, 60))
    for slot in plan.slots:
        window = next(w for w in windows if w.starts_at == slot.window_starts_at)
        assert slot.ends_at <= window.ends_at


def test_a_piece_of_work_needs_a_weld_and_some_time():
    with pytest.raises(ValidationError):
        Piece(weld="  ", source_out=minutes(10))
    with pytest.raises(ValidationError):
        Piece(weld="GW-100", source_out=Duration.ZERO)


def test_a_negative_setup_is_refused():
    with pytest.raises(ValidationError):
        plan_shift(schedule(), work(30), setup=Duration(-1))


def test_no_work_at_all_produces_an_empty_plan():
    plan = plan_shift(schedule(), ())
    assert plan.is_complete
    assert plan.slots == ()
    assert plan.windows_used == 0


def test_the_utilisation_counts_the_setup_as_well_as_the_shots():
    windows = schedule().windows
    plan = plan_shift(schedule(), work(30, 30))
    used = window_utilisation(plan, windows[0])
    assert used == minutes(100)
    assert DEFAULT_SETUP == minutes(40)


def test_an_unused_window_reports_no_utilisation():
    windows = schedule(MONDAY, date(2025, 1, 28)).windows
    plan = plan_shift(schedule(MONDAY, date(2025, 1, 28)), work(30))
    assert window_utilisation(plan, windows[1]) == Duration.ZERO


def test_the_spare_time_is_what_is_left_of_the_window():
    windows = schedule().windows
    plan = plan_shift(schedule(), work(30, 30))
    assert spare_in(plan, windows[0]) == minutes(350)


def test_a_full_window_has_no_spare_time():
    windows = schedule().windows
    plan = plan_shift(schedule(), work(*([100] * 4)))
    assert spare_in(plan, windows[0]).ms >= 0


def test_the_plan_prints_what_it_did_and_what_it_could_not():
    assert plan_shift(schedule(), work(30, 30)).describe() == "2 joints across 1 windows"
    rolled = plan_shift(schedule(), work(*([120] * 5)))
    assert "left over" in rolled.describe()
