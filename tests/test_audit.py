from datetime import datetime, timedelta

import pytest

from qu_imager.audit import Action, Entry, Trail
from qu_imager.errors import ValidationError

WHEN = datetime(2025, 5, 12, 9, 0)
LATER = WHEN + timedelta(hours=1)
LATEST = WHEN + timedelta(hours=2)


def a_change(at=WHEN, actor="0142", was="repair", became="accept", reason=""):
    return Entry(at, actor, Action.CHANGED, "weld", "GW-104", "disposition", was, became, reason)


def test_a_change_names_the_field_and_both_values():
    entry = a_change()
    assert entry.was == "repair"
    assert entry.became == "accept"
    assert entry.field_name == "disposition"


def test_a_change_with_no_actor_is_refused():
    with pytest.raises(ValidationError):
        Entry(WHEN, "  ", Action.CREATED, "weld", "GW-104")


def test_a_change_that_names_no_record_is_refused():
    with pytest.raises(ValidationError):
        Entry(WHEN, "0142", Action.CREATED, "  ", "GW-104")
    with pytest.raises(ValidationError):
        Entry(WHEN, "0142", Action.CREATED, "weld", "  ")


def test_a_change_that_names_no_field_is_refused():
    with pytest.raises(ValidationError):
        Entry(WHEN, "0142", Action.CHANGED, "weld", "GW-104", "", "a", "b")


def test_a_change_that_changed_nothing_is_refused():
    with pytest.raises(ValidationError) as caught:
        a_change(was="accept", became="accept")
    assert caught.value.context["field"] == "disposition"


def test_the_history_of_one_record_reads_oldest_first():
    log = Trail()
    log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept")
    log.created(WHEN, "0142", "weld", "GW-104")
    found = log.for_record("weld", "GW-104")
    assert [e.action for e in found] == [Action.CREATED, Action.CHANGED]


def test_another_record_is_not_in_this_history():
    log = Trail()
    log.created(WHEN, "0142", "weld", "GW-104")
    log.created(WHEN, "0142", "weld", "GW-110")
    assert len(log.for_record("weld", "GW-104")) == 1


def test_what_one_person_did_can_be_pulled_out():
    log = Trail()
    log.created(WHEN, "0142", "weld", "GW-104")
    log.created(WHEN, "0201", "weld", "GW-110")
    assert len(log.by_actor("0142")) == 1
    assert log.by_actor("0303") == ()


def test_a_field_history_reads_without_replaying_the_whole_trail():
    log = Trail()
    log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
    log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept")
    assert log.field_history("weld", "GW-104", "disposition") == ("none", "repair", "accept")


def test_the_current_value_is_the_last_one_it_became():
    log = Trail()
    log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
    log.changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept")
    assert log.current("weld", "GW-104", "disposition") == "accept"


def test_a_field_nobody_touched_has_no_history_and_no_current_value():
    log = Trail()
    log.created(WHEN, "0142", "weld", "GW-104")
    assert log.field_history("weld", "GW-104", "welder") == ()
    assert log.current("weld", "GW-104", "welder") is None


def test_nothing_changed_after_signing_when_nothing_was_signed():
    log = Trail()
    log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
    assert log.changed_after_signing("weld", "GW-104") == ()


def test_a_change_after_the_signature_is_what_a_dispute_is_about():
    log = Trail()
    log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
    log.signed(LATER, "0303", "weld", "GW-104")
    log.changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept")
    after = log.changed_after_signing("weld", "GW-104")
    assert len(after) == 1
    assert after[0].actor == "0201"


def test_a_change_before_the_signature_is_not():
    log = Trail()
    log.changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair")
    log.signed(LATER, "0303", "weld", "GW-104")
    assert log.changed_after_signing("weld", "GW-104") == ()


def test_an_entry_prints_the_way_the_trail_reads():
    text = a_change(reason="client agreed").describe()
    assert "2025-05-12 09:00 0142 weld GW-104 disposition repair to accept" in text
    assert "(client agreed)" in text


def test_a_creation_prints_without_field_detail():
    log = Trail()
    log.created(WHEN, "0142", "weld", "GW-104")
    assert log.entries[0].describe().endswith("weld GW-104 created")


def test_an_empty_value_prints_as_nothing_rather_than_a_gap():
    entry = Entry(WHEN, "0142", Action.CHANGED, "weld", "GW-104", "welder", "", "w-19")
    assert "welder nothing to w-19" in entry.describe()
