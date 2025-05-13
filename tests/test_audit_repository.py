from datetime import datetime, timedelta

from qu_imager.audit import Action
from qu_imager.persistence.unit_of_work import UnitOfWork, transaction

from conftest import database

WHEN = datetime(2025, 5, 13, 8, 30)
LATER = WHEN + timedelta(hours=2)
LATEST = WHEN + timedelta(hours=4)


def test_an_appended_entry_comes_back():
    with UnitOfWork(database()) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        found = work.audit.for_record("weld", "GW-104")
    assert len(found) == 1
    assert found[0].action is Action.CREATED
    assert found[0].actor == "0142"


def test_a_history_reads_oldest_first_even_when_written_out_of_order():
    with UnitOfWork(database()) as work:
        work.audit.changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept")
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        actions = [e.action for e in work.audit.for_record("weld", "GW-104")]
    assert actions == [Action.CREATED, Action.CHANGED]


def test_two_entries_at_the_same_instant_keep_the_order_they_were_written():
    with UnitOfWork(database()) as work:
        work.audit.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
        work.audit.changed(WHEN, "0142", "weld", "GW-104", "disposition", "repair", "accept")
        became = [e.became for e in work.audit.for_record("weld", "GW-104")]
    assert became == ["repair", "accept"]


def test_another_record_is_not_in_this_history():
    with UnitOfWork(database()) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        work.audit.created(WHEN, "0142", "weld", "GW-110")
        assert len(work.audit.for_record("weld", "GW-104")) == 1


def test_one_persons_day_can_be_pulled_out():
    with UnitOfWork(database()) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        work.audit.created(LATEST, "0201", "weld", "GW-110")
        mine = work.audit.by_actor("0142", WHEN, WHEN + timedelta(days=1))
    assert len(mine) == 1


def test_a_day_that_ends_before_the_entry_does_not_include_it():
    with UnitOfWork(database()) as work:
        work.audit.created(LATER, "0142", "weld", "GW-104")
        assert work.audit.by_actor("0142", WHEN, LATER) == ()


def test_the_current_value_is_the_last_thing_the_field_became():
    with UnitOfWork(database()) as work:
        work.audit.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
        work.audit.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept")
        assert work.audit.current("weld", "GW-104", "disposition") == "accept"


def test_a_field_nobody_touched_has_no_current_value():
    with UnitOfWork(database()) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        assert work.audit.current("weld", "GW-104", "welder") is None


def test_a_change_after_the_signature_is_found():
    with UnitOfWork(database()) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
        work.audit.signed(LATER, "0303", "weld", "GW-104")
        work.audit.changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept")
        after = work.audit.changed_after_signing("weld", "GW-104")
    assert [e.actor for e in after] == ["0201"]


def test_nothing_is_found_after_a_signature_nobody_gave():
    with UnitOfWork(database()) as work:
        work.audit.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
        assert work.audit.changed_after_signing("weld", "GW-104") == ()


def test_the_trail_survives_the_transaction_that_wrote_it():
    made = database()
    with transaction(made) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
    with UnitOfWork(made) as work:
        assert len(work.audit.for_record("weld", "GW-104")) == 1


def test_a_transaction_that_did_not_reach_the_end_leaves_no_entry():
    made = database()
    with UnitOfWork(made) as work:
        work.audit.created(WHEN, "0142", "weld", "GW-104")
    with UnitOfWork(made) as work:
        assert work.audit.for_record("weld", "GW-104") == ()


def test_a_closed_unit_of_work_refuses_the_trail():
    import pytest

    from qu_imager.errors import ConflictError

    work = UnitOfWork(database())
    with pytest.raises(ConflictError):
        work.audit.created(WHEN, "0142", "weld", "GW-104")
