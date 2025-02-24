from datetime import datetime, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.safety.incidents import Incident, Occurrence, Register

FOUND = datetime(2025, 2, 24, 23, 40)


def incident(reference="INC-1", occurrence=Occurrence.OVEREXPOSURE, found=FOUND, notified=None):
    return Incident(
        reference=reference,
        occurrence=occurrence,
        discovered_at=found,
        site="Come By Chance",
        reported_by="0142",
        account="the source hung up in the guide tube",
        notified_at=notified,
    )


def test_the_clock_starts_when_the_shop_found_out():
    entry = incident(occurrence=Occurrence.OVEREXPOSURE)
    assert entry.due_at == FOUND + timedelta(hours=24)


def test_a_stuck_source_is_an_immediate_call():
    entry = incident(occurrence=Occurrence.SOURCE_DISCONNECTED)
    assert entry.occurrence.is_immediate
    assert entry.due_at == FOUND + timedelta(hours=1)


def test_a_meter_found_out_of_calibration_is_a_written_report():
    entry = incident(occurrence=Occurrence.METER_OUT_OF_CALIBRATION)
    assert entry.occurrence.needs_a_written_report
    assert not entry.occurrence.is_immediate
    assert entry.due_at == FOUND + timedelta(days=30)


def test_the_deadlines_are_not_all_the_same():
    deadlines = {occurrence.deadline for occurrence in Occurrence}
    assert len(deadlines) > 3


def test_an_incident_not_yet_reported_counts_down():
    entry = incident()
    assert entry.time_left_at(FOUND + timedelta(hours=1)) == timedelta(hours=23)
    assert not entry.is_notified


def test_reporting_it_stops_the_clock():
    entry = incident()
    entry.notify(FOUND + timedelta(hours=2))
    assert entry.is_notified
    assert entry.time_left_at(FOUND + timedelta(days=5)) == timedelta(0)


def test_something_reported_inside_the_deadline_is_not_late():
    entry = incident()
    entry.notify(FOUND + timedelta(hours=2))
    assert not entry.is_late_at(FOUND + timedelta(days=5))


def test_something_reported_after_the_deadline_stays_late_forever():
    entry = incident()
    entry.notify(FOUND + timedelta(hours=30))
    assert entry.is_late_at(FOUND + timedelta(days=5))


def test_something_unreported_becomes_late_when_the_deadline_passes():
    entry = incident()
    assert not entry.is_late_at(FOUND + timedelta(hours=23))
    assert entry.is_late_at(FOUND + timedelta(hours=25))


def test_an_incident_cannot_be_reported_before_it_was_found():
    with pytest.raises(ValidationError):
        incident(notified=FOUND - timedelta(hours=1))
    entry = incident()
    with pytest.raises(ValidationError):
        entry.notify(FOUND - timedelta(hours=1))


def test_an_incident_is_only_reported_once():
    entry = incident()
    entry.notify(FOUND + timedelta(hours=1))
    with pytest.raises(ConflictError):
        entry.notify(FOUND + timedelta(hours=2))


def test_an_incident_needs_every_field_filled_in():
    for missing in ("reference", "site", "reported_by", "account"):
        with pytest.raises(ValidationError) as caught:
            Incident(
                reference="" if missing == "reference" else "INC-1",
                occurrence=Occurrence.OVEREXPOSURE,
                discovered_at=FOUND,
                site="" if missing == "site" else "s",
                reported_by="" if missing == "reported_by" else "0142",
                account="" if missing == "account" else "a",
            )
        assert caught.value.context["field"] == missing


def test_a_reference_is_used_once():
    register = Register()
    register.record(incident("INC-1"))
    with pytest.raises(ConflictError):
        register.record(incident("INC-1"))


def test_an_incident_that_is_not_there_is_refused():
    with pytest.raises(ConflictError):
        Register().get("INC-9")


def test_the_outstanding_list_comes_back_soonest_deadline_first():
    register = Register()
    register.record(incident("INC-1", Occurrence.METER_OUT_OF_CALIBRATION))
    register.record(incident("INC-2", Occurrence.SOURCE_DISCONNECTED))
    register.record(incident("INC-3", Occurrence.OVEREXPOSURE))
    order = [i.reference for i in register.outstanding_at(FOUND)]
    assert order == ["INC-2", "INC-3", "INC-1"]


def test_something_reported_drops_off_the_outstanding_list():
    register = Register()
    register.record(incident("INC-1"))
    register.get("INC-1").notify(FOUND + timedelta(hours=1))
    assert register.outstanding_at(FOUND + timedelta(hours=2)) == ()


def test_the_late_list_names_what_is_past_its_deadline():
    register = Register()
    register.record(incident("INC-1", Occurrence.SOURCE_DISCONNECTED))
    register.record(incident("INC-2", Occurrence.METER_OUT_OF_CALIBRATION))
    assert register.late_at(FOUND + timedelta(hours=2)) == ("INC-1",)


def test_the_immediate_list_is_what_somebody_is_on_the_phone_about():
    register = Register()
    register.record(incident("INC-1", Occurrence.SOURCE_DISCONNECTED))
    register.record(incident("INC-2", Occurrence.OVEREXPOSURE))
    assert register.immediate_at(FOUND) == ("INC-1",)
    register.get("INC-1").notify(FOUND + timedelta(minutes=10))
    assert register.immediate_at(FOUND) == ()


def test_an_incident_prints_what_is_owed():
    assert "due 2025-02-25 23:40" in incident().describe()
    entry = incident()
    entry.notify(FOUND)
    assert entry.describe().endswith("reported")
