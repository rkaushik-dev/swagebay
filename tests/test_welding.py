from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.quantities.length import Length, mm
from qu_imager.welding import (
    CONTINUITY_DAYS,
    Position,
    Process,
    Qualification,
    Register,
    thickness_range,
)

QUALIFIED = date(2024, 6, 1)


def qualification(
    welder="w-19",
    process=Process.SMAW,
    position=Position.ALL,
    coupon=None,
    on=QUALIFIED,
    group="P1",
):
    return Qualification(
        welder=welder,
        process=process,
        position=position,
        coupon=coupon or mm(12),
        qualified_on=on,
        material_group=group,
    )


def test_a_flat_coupon_buys_only_flat_work():
    assert Position.FLAT.qualifies == frozenset({Position.FLAT})


def test_an_all_position_coupon_buys_everything():
    assert Position.ALL.qualifies == frozenset(Position)


def test_the_positions_nest():
    assert Position.FLAT.qualifies < Position.HORIZONTAL.qualifies
    assert Position.HORIZONTAL.qualifies < Position.VERTICAL.qualifies


def test_a_thin_coupon_qualifies_from_nothing_up_to_twice_itself():
    low, high = thickness_range(mm(8))
    assert low == Length.ZERO
    assert high == mm(16)


def test_a_thick_coupon_carries_a_floor_as_well_as_a_ceiling():
    low, high = thickness_range(mm(25))
    assert low == mm(5)
    assert high == mm(50)


def test_the_range_is_not_symmetric_about_the_coupon():
    low, high = thickness_range(mm(25))
    assert high - mm(25) > mm(25) - low


def test_a_coupon_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        thickness_range(Length.ZERO)
    with pytest.raises(ValidationError):
        qualification(coupon=Length.ZERO)


def test_a_qualification_needs_a_welder():
    with pytest.raises(ValidationError):
        qualification(welder="  ")


def test_a_qualification_covers_what_its_ranges_cover():
    record = qualification(position=Position.VERTICAL, coupon=mm(12))
    assert record.covers(Position.HORIZONTAL, mm(10), "P1")
    assert not record.covers(Position.ALL, mm(10), "P1")
    assert not record.covers(Position.FLAT, mm(30), "P1")
    assert not record.covers(Position.FLAT, mm(10), "P8")


def test_a_qualification_prints_its_range():
    text = qualification(coupon=mm(12)).describe()
    assert "w-19" in text
    assert "5.0 mm to 24.0 mm" in text
    assert "group P1" in text


def test_a_welder_may_weld_inside_their_range_while_it_is_current():
    register = Register()
    register.add(qualification())
    assert register.may_weld("w-19", Process.SMAW, Position.VERTICAL, mm(12), "P1", QUALIFIED)


def test_a_welder_may_not_weld_outside_the_thickness_range():
    register = Register()
    register.add(qualification(coupon=mm(8)))
    assert not register.may_weld("w-19", Process.SMAW, Position.FLAT, mm(20), "P1", QUALIFIED)


def test_a_welder_may_not_weld_a_process_they_never_qualified_on():
    register = Register()
    register.add(qualification(process=Process.SMAW))
    assert not register.may_weld("w-19", Process.GTAW, Position.FLAT, mm(12), "P1", QUALIFIED)


def test_a_qualification_lapses_silently_after_six_months():
    register = Register()
    register.add(qualification())
    assert register.is_current_on("w-19", Process.SMAW, QUALIFIED + timedelta(days=179))
    assert not register.is_current_on("w-19", Process.SMAW, QUALIFIED + timedelta(days=180))
    assert CONTINUITY_DAYS == 180


def test_using_the_process_pushes_the_lapse_date_out():
    register = Register()
    register.add(qualification())
    register.record_use("w-19", Process.SMAW, QUALIFIED + timedelta(days=100))
    assert register.is_current_on("w-19", Process.SMAW, QUALIFIED + timedelta(days=250))


def test_an_earlier_use_does_not_pull_the_lapse_date_back():
    register = Register()
    register.add(qualification())
    register.record_use("w-19", Process.SMAW, QUALIFIED + timedelta(days=100))
    register.record_use("w-19", Process.SMAW, QUALIFIED + timedelta(days=20))
    assert register.lapses_on("w-19", Process.SMAW) == QUALIFIED + timedelta(days=280)


def test_a_use_by_somebody_not_qualified_is_refused():
    register = Register()
    with pytest.raises(ConflictError):
        register.record_use("w-99", Process.SMAW, QUALIFIED)


def test_the_same_qualification_twice_is_refused():
    register = Register()
    register.add(qualification())
    with pytest.raises(ConflictError):
        register.add(qualification())
    register.add(qualification(on=QUALIFIED + timedelta(days=1)))


def test_the_lapsed_list_names_the_welder_and_the_process():
    register = Register()
    register.add(qualification(welder="w-19", process=Process.SMAW))
    register.add(qualification(welder="w-04", process=Process.GTAW))
    register.record_use("w-19", Process.SMAW, QUALIFIED + timedelta(days=170))
    lapsed = register.lapsed_on(QUALIFIED + timedelta(days=200))
    assert lapsed == (("w-04", Process.GTAW),)


def test_a_welder_with_nothing_on_the_register_has_no_lapse_date():
    assert Register().lapses_on("w-99", Process.SMAW) is None


def test_every_qualification_a_welder_holds_comes_back():
    register = Register()
    register.add(qualification(process=Process.SMAW))
    register.add(qualification(process=Process.GTAW))
    assert len(register.for_welder("w-19")) == 2
    assert register.for_welder("w-99") == ()
