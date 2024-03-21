from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.identity import ClockPosition, JobNumber, WeldId


def test_a_job_number_round_trips_through_its_canonical_form():
    job = JobNumber.parse("24-0417")
    assert job.year == 24
    assert job.serial == 417
    assert str(job) == "24-0417"
    assert JobNumber.parse(str(job)) == job


def test_job_numbers_sort_by_year_then_serial():
    numbers = [JobNumber(25, 3), JobNumber(24, 900), JobNumber(24, 12)]
    assert [str(j) for j in sorted(numbers)] == ["24-0012", "24-0900", "25-0003"]


def test_the_next_job_number_is_the_next_serial_and_it_can_run_out():
    assert JobNumber(24, 417).next() == JobNumber(24, 418)
    with pytest.raises(ValidationError):
        JobNumber(24, 9999).next()


def test_a_job_number_that_is_not_shaped_right_is_refused():
    for bad in ["", "240417", "2024-0417", "24-417", "24-04177", "ab-0417"]:
        with pytest.raises(ValidationError):
            JobNumber.parse(bad)


def test_a_weld_identifier_carries_its_repair_round():
    original = WeldId.parse("GW-104")
    assert not original.is_repair
    first = WeldId.parse("GW-104R1")
    assert first.is_repair
    assert first.repair == 1
    assert first.original == original


def test_a_repair_is_a_round_of_the_same_weld_not_a_new_one():
    original = WeldId.parse("GW-104")
    first = original.next_repair()
    second = first.next_repair()
    assert str(first) == "GW-104R1"
    assert str(second) == "GW-104R2"
    assert second.original == original
    assert first.number == second.number == original.number


def test_weld_identifiers_are_read_case_insensitively_and_stored_upper():
    assert WeldId.parse(" gw-104r1 ") == WeldId("GW", 104, 1)
    assert str(WeldId.parse("bw-7")) == "BW-7"


def test_an_unknown_prefix_is_refused():
    with pytest.raises(ValidationError):
        WeldId.parse("XX-104")
    with pytest.raises(ValidationError):
        WeldId("XX", 104)


def test_a_weld_identifier_that_is_not_shaped_right_is_refused():
    for bad in ["", "GW104", "GW-", "GW-0R", "GW-104R", "GW-104-R1"]:
        with pytest.raises(ValidationError):
            WeldId.parse(bad)


def test_the_description_spells_the_prefix_out():
    assert WeldId.parse("GW-104").describe() == "girth weld 104"
    assert WeldId.parse("BW-7R2").describe() == "branch weld 7, repair 2"


def test_welds_sort_with_their_repairs_after_the_original():
    welds = [WeldId("GW", 104, 2), WeldId("GW", 104), WeldId("GW", 103), WeldId("GW", 104, 1)]
    assert [str(w) for w in sorted(welds)] == ["GW-103", "GW-104", "GW-104R1", "GW-104R2"]


def test_a_clock_position_is_read_the_four_ways_a_crew_writes_it():
    assert ClockPosition.parse("12") == ClockPosition(0)
    assert ClockPosition.parse("12:00") == ClockPosition(0)
    assert ClockPosition.parse("top") == ClockPosition(0)
    assert ClockPosition.parse("1200") == ClockPosition(0)
    assert ClockPosition.parse("bottom") == ClockPosition(360)


def test_a_clock_position_carries_minutes():
    assert ClockPosition.parse("1:30") == ClockPosition(90)
    assert ClockPosition.parse("130") == ClockPosition(90)
    assert str(ClockPosition(90)) == "1:30"
    assert str(ClockPosition(0)) == "12:00"


def test_a_clock_position_converts_to_degrees_round_the_pipe():
    assert ClockPosition.parse("3:00").degrees == Decimal(90)
    assert ClockPosition.parse("6:00").degrees == Decimal(180)
    assert ClockPosition.from_degrees(Decimal(270)) == ClockPosition.parse("9:00")
    assert ClockPosition.from_degrees(Decimal(450)) == ClockPosition.parse("3:00")


def test_the_arc_between_two_positions_runs_clockwise():
    twelve = ClockPosition.parse("12:00")
    three = ClockPosition.parse("3:00")
    assert twelve.arc_to(three) == 180
    assert three.arc_to(twelve) == 540
    assert twelve.arc_to(twelve) == 0


def test_a_clock_position_that_is_not_on_the_dial_is_refused():
    for bad in ["", "13:00", "0:30", "12:60", "later", "-1"]:
        with pytest.raises(ValidationError):
            ClockPosition.parse(bad)
    with pytest.raises(ValidationError):
        ClockPosition(720)
