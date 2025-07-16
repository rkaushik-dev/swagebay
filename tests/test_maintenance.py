from datetime import date, timedelta

import pytest

from qu_imager.errors import ValidationError
from qu_imager.safety.maintenance import (
    QUARTERLY_DAYS,
    Defect,
    Inspection,
    Item,
    MaintenanceRegister,
    Severity,
    pre_use,
    quarterly,
    safety_items,
)

STRIPPED = date(2025, 4, 14)
MORNING = date(2025, 6, 16)
CABLE = Defect(Item.DRIVE_CABLE, "three strands broken 200 mm from the connector")
WHEEL = Defect(Item.WHEELS, "one castor seized")


def test_a_fault_on_a_safety_item_stops_the_device():
    assert Item.DRIVE_CABLE.stops_the_device
    assert Item.SOURCE_LOCK.severity is Severity.STOPS_THE_DEVICE
    assert CABLE.stops_the_device


def test_a_fault_on_the_wheels_does_not():
    assert not Item.WHEELS.stops_the_device
    assert not WHEEL.stops_the_device


def test_the_safety_items_are_the_ones_that_stop_it():
    assert Item.GUIDE_TUBE in safety_items()
    assert Item.LABELS not in safety_items()


def test_a_defect_that_does_not_say_what_is_wrong_is_refused():
    with pytest.raises(ValidationError):
        Defect(Item.WHEELS, "   ")


def test_a_defect_prints_the_item_and_the_account():
    assert WHEEL.describe() == "wheels: one castor seized"


def test_a_pre_use_check_covers_the_safety_items():
    check = pre_use("D-77", MORNING, "0142")
    assert set(check.items_checked) == set(safety_items())
    assert check.passed
    assert check.expires_on() is None


def test_a_quarterly_covers_everything():
    strip = quarterly("D-77", STRIPPED, "0303")
    assert set(strip.items_checked) == set(Item)
    assert strip.expires_on() == STRIPPED + timedelta(days=QUARTERLY_DAYS)


def test_a_quarterly_that_skipped_an_item_is_refused():
    with pytest.raises(ValidationError) as caught:
        Inspection("D-77", STRIPPED, "0303", True, (Item.DRIVE_CABLE,))
    assert "labels" in caught.value.context["missed"]


def test_an_inspection_that_checked_nothing_is_refused():
    with pytest.raises(ValidationError):
        Inspection("D-77", MORNING, "0142", False, ())


def test_an_unsigned_inspection_is_refused():
    with pytest.raises(ValidationError):
        Inspection("D-77", MORNING, "  ", False, safety_items())


def test_an_inspection_with_no_device_is_refused():
    with pytest.raises(ValidationError):
        Inspection("  ", MORNING, "0142", False, safety_items())


def test_the_same_item_twice_on_one_sheet_is_refused():
    with pytest.raises(ValidationError):
        Inspection("D-77", MORNING, "0142", False, (Item.WHEELS, Item.WHEELS))


def test_a_defect_on_an_item_nobody_checked_is_refused():
    with pytest.raises(ValidationError) as caught:
        Inspection("D-77", MORNING, "0142", False, (Item.WHEELS,), (CABLE,))
    assert caught.value.context["item"] == "drive cable"


def test_defects_are_kept_apart_by_what_they_do():
    check = Inspection("D-77", MORNING, "0142", False, tuple(Item), (CABLE, WHEEL))
    assert check.blocking_defects == (CABLE,)
    assert check.noted_defects == (WHEEL,)
    assert not check.passed


def test_a_check_with_only_a_wheel_off_still_passes():
    check = Inspection("D-77", MORNING, "0142", False, tuple(Item), (WHEEL,))
    assert check.passed


def test_an_inspection_prints_the_way_the_sheet_reads():
    assert quarterly("D-77", STRIPPED, "0303").describe().endswith("quarterly passed by 0303")
    failed = pre_use("D-77", MORNING, "0142", (CABLE,))
    assert "pre use failed" in failed.describe()


def test_a_device_with_nothing_on_record_does_not_go_out():
    register = MaintenanceRegister()
    reasons = register.reasons_not_to_use("D-77", MORNING)
    assert "no quarterly inspection on record" in reasons
    assert "no pre use check that morning" in reasons
    assert not register.may_go_out("D-77", MORNING)


def test_a_device_with_both_checks_goes_out():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", MORNING, "0142"))
    assert register.may_go_out("D-77", MORNING)


def test_a_quarterly_that_ran_out_stops_the_device():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", MORNING, "0142"))
    late = STRIPPED + timedelta(days=QUARTERLY_DAYS)
    register.record(pre_use("D-77", late, "0142"))
    assert not register.may_go_out("D-77", late)
    assert "ran out" in register.reasons_not_to_use("D-77", late)[0]


def test_the_last_day_of_the_quarter_is_still_covered():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    last = STRIPPED + timedelta(days=QUARTERLY_DAYS - 1)
    register.record(pre_use("D-77", last, "0142"))
    assert register.may_go_out("D-77", last)


def test_yesterdays_pre_use_check_is_not_todays():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", MORNING - timedelta(days=1), "0142"))
    assert "no pre use check that morning" in register.reasons_not_to_use("D-77", MORNING)


def test_a_broken_drive_cable_that_morning_stops_it():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", MORNING, "0142", (CABLE,)))
    assert "drive cable" in register.reasons_not_to_use("D-77", MORNING)[0]


def test_a_quarterly_that_found_something_blocking_stops_it_until_the_next_one():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303", (CABLE,)))
    register.record(pre_use("D-77", MORNING, "0142"))
    assert not register.may_go_out("D-77", MORNING)


def test_a_later_clean_quarterly_clears_it():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303", (CABLE,)))
    register.record(quarterly("D-77", MORNING - timedelta(days=1), "0303"))
    register.record(pre_use("D-77", MORNING, "0142"))
    assert register.may_go_out("D-77", MORNING)


def test_another_device_is_not_covered_by_this_ones_paperwork():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", MORNING, "0142"))
    assert not register.may_go_out("D-78", MORNING)


def test_the_fleet_can_be_asked_which_devices_are_overdue():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    assert register.overdue_on(("D-77", "D-78"), MORNING) == ("D-78",)
    late = STRIPPED + timedelta(days=QUARTERLY_DAYS + 1)
    assert register.overdue_on(("D-77",), late) == ("D-77",)


def test_the_next_quarterly_date_reads_off_the_last_one():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    assert register.quarterly_due_by("D-77") == STRIPPED + timedelta(days=QUARTERLY_DAYS)
    assert register.quarterly_due_by("D-78") is None


def test_the_history_of_one_device_reads_in_date_order():
    register = MaintenanceRegister()
    register.record(pre_use("D-77", MORNING, "0142"))
    register.record(quarterly("D-77", STRIPPED, "0303"))
    days = [i.performed_on for i in register.for_device("D-77")]
    assert days == sorted(days)


def test_a_record_falls_out_of_retention_after_three_years():
    strip = quarterly("D-77", STRIPPED, "0303")
    assert strip.destroy_after() == STRIPPED + timedelta(days=3 * 365)


def test_a_quarterly_does_not_cover_the_day_before_it_was_done():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    day_before = STRIPPED - timedelta(days=1)
    register.record(pre_use("D-77", day_before, "0142"))
    assert not register.may_go_out("D-77", day_before)
    assert "no quarterly inspection on record" in register.reasons_not_to_use("D-77", day_before)


def test_the_day_it_was_done_is_covered():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    register.record(pre_use("D-77", STRIPPED, "0142"))
    assert register.may_go_out("D-77", STRIPPED)


def test_the_latest_quarterly_is_the_latest_as_of_the_day_asked_about():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    later = STRIPPED + timedelta(days=40)
    register.record(quarterly("D-77", later, "0303"))
    assert register.latest_quarterly("D-77", STRIPPED + timedelta(days=1)).performed_on == STRIPPED
    assert register.latest_quarterly("D-77", later).performed_on == later
    assert register.latest_quarterly("D-77").performed_on == later


def test_the_next_due_date_is_answered_as_of_the_day_asked_about():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    assert register.quarterly_due_by("D-77", STRIPPED - timedelta(days=1)) is None


def test_a_fleet_listing_does_not_credit_a_future_quarterly():
    register = MaintenanceRegister()
    register.record(quarterly("D-77", STRIPPED, "0303"))
    assert register.overdue_on(("D-77",), STRIPPED - timedelta(days=1)) == ("D-77",)
