from datetime import datetime
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.dose import Dose, usv
from qu_imager.safety.dosimetry import WorkerClass
from qu_imager.services.dose import (
    badges_in_year,
    collective_dose,
    days_left_at_this_rate,
    monthly_sheet,
    over_investigation_level,
    position,
    record_dose,
    taken_in_month,
    taken_in_year,
)

from conftest import database


def put(work, badge="0142", msv="1", month=3, source="badge"):
    record_dose(
        work,
        badge,
        2024,
        Dose.from_msv(Decimal(msv)),
        datetime(2024, month, 15, 9, 0),
        source,
    )


def test_entries_add_up_across_the_year():
    made = database()
    with transaction(made) as work:
        put(work, msv="1", month=1)
        put(work, msv="2", month=6)
        assert taken_in_year(work, "0142", 2024) == Dose.from_msv(3)


def test_somebody_with_no_entries_has_taken_nothing():
    made = database()
    with transaction(made) as work:
        assert taken_in_year(work, "0999", 2024) == Dose.ZERO


def test_a_month_is_summed_on_its_own():
    made = database()
    with transaction(made) as work:
        put(work, msv="1", month=3)
        put(work, msv="2", month=4)
        assert taken_in_month(work, "0142", 2024, 3) == Dose.from_msv(1)
        assert taken_in_month(work, "0142", 2024, 4) == Dose.from_msv(2)
        assert taken_in_month(work, "0142", 2024, 5) == Dose.ZERO


def test_december_does_not_run_off_the_end_of_the_year():
    made = database()
    with transaction(made) as work:
        put(work, msv="1", month=12)
        assert taken_in_month(work, "0142", 2024, 12) == Dose.from_msv(1)


def test_a_month_outside_the_calendar_is_refused():
    made = database()
    with transaction(made) as work:
        with pytest.raises(ValidationError):
            taken_in_month(work, "0142", 2024, 13)
        with pytest.raises(ValidationError):
            taken_in_month(work, "0142", 2024, 0)


def test_a_dose_needs_a_badge_and_a_source():
    made = database()
    with transaction(made) as work:
        with pytest.raises(ValidationError):
            record_dose(work, "  ", 2024, usv(10), datetime(2024, 3, 1), "badge")
        with pytest.raises(ValidationError):
            record_dose(work, "0142", 2024, usv(10), datetime(2024, 3, 1), "  ")


def test_a_quiet_year_and_a_bad_month_disagree_and_both_are_right():
    made = database()
    with transaction(made) as work:
        put(work, msv="0.2", month=1)
        put(work, msv="4", month=7)
        assert taken_in_month(work, "0142", 2024, 7) == Dose.from_msv(4)
        assert position(work, "0142", 2024).investigation_level == 0


def test_the_investigation_levels_read_against_the_annual_position():
    made = database()
    with transaction(made) as work:
        put(work, msv="6")
        assert position(work, "0142", 2024).investigation_level == 1
        put(work, msv="10", month=8)
        assert position(work, "0142", 2024).investigation_level == 2


def test_the_limit_depends_on_the_worker_class():
    made = database()
    with transaction(made) as work:
        put(work, msv="4")
        adult = position(work, "0142", 2024)
        expecting = position(work, "0142", 2024, WorkerClass.DECLARED_PREGNANT)
        assert adult.investigation_level == 0
        assert expecting.investigation_level == 2
        assert expecting.limit < adult.limit


def test_what_is_left_of_the_year_runs_down_and_stops_at_zero():
    made = database()
    with transaction(made) as work:
        put(work, msv="20")
        assert position(work, "0142", 2024).remaining == Dose.from_msv(30)
        put(work, msv="40", month=9)
        assert position(work, "0142", 2024).remaining == Dose.ZERO


def test_the_badges_with_entries_come_back_sorted():
    made = database()
    with transaction(made) as work:
        put(work, badge="0201")
        put(work, badge="0142")
        assert badges_in_year(work, 2024) == ("0142", "0201")


def test_the_people_over_a_level_come_back_worst_first():
    made = database()
    with transaction(made) as work:
        put(work, badge="0142", msv="6")
        put(work, badge="0201", msv="16")
        put(work, badge="0303", msv="1")
        over = over_investigation_level(work, 2024, level=1)
        assert [p.badge for p in over] == ["0201", "0142"]
        assert [p.badge for p in over_investigation_level(work, 2024, level=2)] == ["0201"]


def test_there_are_only_two_investigation_levels():
    made = database()
    with transaction(made) as work, pytest.raises(ValidationError):
        over_investigation_level(work, 2024, level=3)


def test_the_monthly_sheet_lists_everybody_in_badge_order():
    made = database()
    with transaction(made) as work:
        put(work, badge="0201", msv="2", month=5)
        put(work, badge="0142", msv="1", month=5)
        sheet = monthly_sheet(work, 2024, 5)
        assert [badge for badge, _taken in sheet] == ["0142", "0201"]
        assert sheet[1][1] == Dose.from_msv(2)


def test_the_collective_dose_is_the_whole_shop():
    made = database()
    with transaction(made) as work:
        put(work, badge="0142", msv="2")
        put(work, badge="0201", msv="3")
        assert collective_dose(work, 2024) == Dose.from_msv(5)
        assert collective_dose(work, 2025) == Dose.ZERO


def test_the_burn_down_says_how_long_the_year_lasts_at_this_rate():
    made = database()
    with transaction(made) as work:
        put(work, msv="5")
        left = days_left_at_this_rate(position(work, "0142", 2024), days_elapsed=100)
        assert left == 900


def test_somebody_who_has_taken_nothing_has_no_burn_down():
    made = database()
    with transaction(made) as work:
        assert days_left_at_this_rate(position(work, "0999", 2024), 100) is None


def test_a_burn_down_needs_some_of_the_year_to_have_gone():
    made = database()
    with transaction(made) as work, pytest.raises(ValidationError):
        days_left_at_this_rate(position(work, "0142", 2024), 0)


def test_a_position_prints_the_way_the_board_reads():
    made = database()
    with transaction(made) as work:
        put(work, msv="6")
        assert position(work, "0142", 2024).describe() == "0142 6.00 mSv of 50 mSv, level 1"
