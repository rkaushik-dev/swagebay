from datetime import date, timedelta

import pytest

from qu_imager.errors import ValidationError
from qu_imager.persistence.maintenance_repository import row_to_inspection
from qu_imager.persistence.models.maintenance import InspectionRow
from qu_imager.persistence.unit_of_work import UnitOfWork, transaction
from qu_imager.safety.maintenance import Defect, Item, pre_use, quarterly

from conftest import database

STRIPPED = date(2025, 4, 14)
MORNING = date(2025, 6, 19)
CABLE = Defect(Item.DRIVE_CABLE, "three strands broken near the connector")
WHEEL = Defect(Item.WHEELS, "one castor seized")


def test_an_inspection_comes_back_the_way_it_went_in():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303", (WHEEL,)))
        found = work.maintenance.for_device("D-77")
    assert len(found) == 1
    assert found[0].is_quarterly
    assert found[0].performed_by == "0303"
    assert found[0].defects == (WHEEL,)


def test_the_items_checked_survive_the_round_trip():
    with UnitOfWork(database()) as work:
        work.maintenance.record(pre_use("D-77", MORNING, "0142"))
        found = work.maintenance.for_device("D-77")[0]
    assert Item.DRIVE_CABLE in found.items_checked
    assert Item.WHEELS not in found.items_checked


def test_a_pre_use_check_is_not_stored_as_a_quarterly():
    with UnitOfWork(database()) as work:
        work.maintenance.record(pre_use("D-77", MORNING, "0142"))
        assert not work.maintenance.for_device("D-77")[0].is_quarterly


def test_two_defects_on_one_inspection_both_come_back():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303", (CABLE, WHEEL)))
        found = work.maintenance.for_device("D-77")[0]
    assert len(found.defects) == 2
    assert found.blocking_defects == (CABLE,)


def test_another_devices_inspections_are_not_in_this_ones_history():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        work.maintenance.record(quarterly("D-78", STRIPPED, "0303"))
        assert len(work.maintenance.for_device("D-77")) == 1


def test_the_history_reads_in_date_order_whatever_order_it_was_written():
    with UnitOfWork(database()) as work:
        work.maintenance.record(pre_use("D-77", MORNING, "0142"))
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        days = [i.performed_on for i in work.maintenance.for_device("D-77")]
    assert days == [STRIPPED, MORNING]


def test_a_register_answers_the_morning_question():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        work.maintenance.record(pre_use("D-77", MORNING, "0142"))
        register = work.maintenance.register_for(("D-77",))
    assert register.may_go_out("D-77", MORNING)
    assert not register.may_go_out("D-77", MORNING + timedelta(days=1))


def test_a_register_for_nothing_is_empty_rather_than_everything():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        assert work.maintenance.register_for(()).inspections == []


def test_a_register_only_loads_the_devices_it_was_asked_for():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        work.maintenance.record(quarterly("D-78", STRIPPED, "0303"))
        register = work.maintenance.register_for(("D-77",))
    assert len(register.inspections) == 1


def test_every_device_that_has_had_a_cable_fault_can_be_listed():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303", (CABLE,)))
        work.maintenance.record(quarterly("D-78", STRIPPED, "0303", (WHEEL,)))
        found = work.maintenance.defects_on(Item.DRIVE_CABLE)
    assert found == (("D-77", "three strands broken near the connector"),)


def test_an_item_nothing_has_ever_failed_lists_nothing():
    with UnitOfWork(database()) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303"))
        assert work.maintenance.defects_on(Item.GUIDE_TUBE) == ()


def test_an_inspection_survives_the_transaction_that_wrote_it():
    made = database()
    with transaction(made) as work:
        work.maintenance.record(quarterly("D-77", STRIPPED, "0303", (CABLE,)))
    with UnitOfWork(made) as work:
        assert work.maintenance.for_device("D-77")[0].defects == (CABLE,)


def test_a_row_naming_an_item_nothing_inspects_is_refused_on_the_way_out():
    row = InspectionRow(
        device_serial="D-77",
        performed_on=MORNING,
        performed_by="0142",
        is_quarterly=False,
        items_checked="FLUX_CAPACITOR",
        notes="",
    )
    with pytest.raises(ValidationError):
        row_to_inspection(row)
