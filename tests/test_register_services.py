from datetime import date, datetime, timedelta

import pytest

from qu_imager.calibration import Certificate, Instrument
from qu_imager.errors import ConflictError, NotFoundError, ValidationError
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.length import mm
from qu_imager.reporting.archive import Filed, Location, Retention
from qu_imager.safety.incidents import Incident, Occurrence
from qu_imager.services.registers import (
    file_radiograph,
    issue_film,
    notify_incident,
    receive_lot,
    record_calibration,
    record_incident,
    record_process_use,
    record_qualification,
    reorder_needed,
    sheets_on_hand,
)
from qu_imager.stock import Lot
from qu_imager.welding import Position, Process, Qualification

from conftest import database

DAY = date(2025, 4, 1)
MOMENT = datetime(2025, 4, 1, 23, 30)
CERTIFICATE = Certificate(DAY, DAY + timedelta(days=365), "NRC", "C-1")


def an_incident(reference="INC-1"):
    return Incident(
        reference=reference,
        occurrence=Occurrence.OVEREXPOSURE,
        discovered_at=MOMENT,
        site="Come By Chance",
        reported_by="0142",
        account="the source hung up",
    )


def a_lot(number="L-1", sheets=100, expires_in=200):
    return Lot(
        lot_number=number,
        film_key="d7",
        size="4x10",
        sheets=sheets,
        received_on=DAY - timedelta(days=30),
        expires_on=DAY + timedelta(days=expires_in),
    )


def a_qualification():
    return Qualification("w-19", Process.SMAW, Position.ALL, mm(12), DAY, "P1")


def test_an_incident_is_recorded_once():
    made = database()
    with transaction(made) as work:
        record_incident(work, an_incident())
        assert work.incidents.get("INC-1").site == "Come By Chance"
        with pytest.raises(ConflictError):
            record_incident(work, an_incident())


def test_reporting_an_incident_stops_its_clock():
    made = database()
    with transaction(made) as work:
        record_incident(work, an_incident())
        notify_incident(work, "INC-1", MOMENT + timedelta(hours=2))
        assert work.incidents.unreported() == ()


def test_an_incident_is_only_reported_once_and_never_backwards():
    made = database()
    with transaction(made) as work:
        record_incident(work, an_incident())
        with pytest.raises(ValidationError):
            notify_incident(work, "INC-1", MOMENT - timedelta(hours=1))
        notify_incident(work, "INC-1", MOMENT)
        with pytest.raises(ConflictError):
            notify_incident(work, "INC-1", MOMENT + timedelta(hours=1))


def test_reporting_an_incident_that_is_not_there_is_refused():
    made = database()
    with pytest.raises(NotFoundError), transaction(made) as work:
        notify_incident(work, "INC-9", MOMENT)


def test_a_calibration_is_recorded_and_the_instrument_stops_being_overdue():
    made = database()
    with transaction(made) as work:
        record_calibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE)
        assert work.calibrations.expired_on(DAY + timedelta(days=30)) == ()
        assert work.calibrations.expired_on(DAY + timedelta(days=400)) == ("LU-1",)


def test_the_same_calibration_twice_is_refused():
    made = database()
    with transaction(made) as work:
        record_calibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE)
        with pytest.raises(ConflictError):
            record_calibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE)


def test_a_calibration_needs_a_serial():
    made = database()
    with pytest.raises(ValidationError), transaction(made) as work:
        record_calibration(work, "  ", Instrument.SURVEY_METER, CERTIFICATE)


def test_film_is_received_and_shows_up_on_hand():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot())
        assert sheets_on_hand(work, DAY, "d7", "4x10") == 100


def test_the_same_lot_twice_is_refused():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot())
        with pytest.raises(ConflictError):
            receive_lot(work, a_lot())


def test_film_is_issued_shortest_dated_first_and_comes_off_the_shelf():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot("L-1", expires_in=300))
        receive_lot(work, a_lot("L-2", expires_in=100))
        assert issue_film(work, DAY, "d7", "4x10", 40) == (("L-2", 40),)
        assert sheets_on_hand(work, DAY, "d7", "4x10") == 160


def test_an_issue_spans_lots_when_one_will_not_cover_it():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot("L-1", sheets=30, expires_in=50))
        receive_lot(work, a_lot("L-2", sheets=100, expires_in=300))
        assert issue_film(work, DAY, "d7", "4x10", 50) == (("L-1", 30), ("L-2", 20))


def test_asking_for_more_film_than_there_is_names_the_shortfall():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot(sheets=20))
        with pytest.raises(ConflictError) as caught:
            issue_film(work, DAY, "d7", "4x10", 50)
        assert caught.value.context["on_hand"] == 20


def test_an_issue_of_nothing_is_refused():
    made = database()
    with pytest.raises(ValidationError), transaction(made) as work:
        issue_film(work, DAY, "d7", "4x10", 0)


def test_the_reorder_point_fires_at_the_floor():
    made = database()
    with transaction(made) as work:
        receive_lot(work, a_lot(sheets=25))
        assert not reorder_needed(work, DAY, "d7", "4x10", 20)
        assert reorder_needed(work, DAY, "d7", "4x10", 25)
        with pytest.raises(ValidationError):
            reorder_needed(work, DAY, "d7", "4x10", -1)


def test_a_radiograph_is_filed_once_and_keeps_its_location():
    made = database()
    entry = Filed(
        radiograph="25-0001-R0001",
        job="25-0001",
        filed_on=DAY,
        retention=Retention.FIVE_YEARS,
        location=Location("Come By Chance", "A", "14"),
    )
    with transaction(made) as work:
        file_radiograph(work, entry)
        assert work.archive.find("25-0001-R0001").box == "14"
        with pytest.raises(ConflictError):
            file_radiograph(work, entry)


def test_a_film_filed_with_no_location_is_reported():
    made = database()
    entry = Filed("25-0001-R0002", "25-0001", DAY, Retention.INDEFINITE, None)
    with transaction(made) as work:
        file_radiograph(work, entry)
        assert work.archive.without_a_location() == ("25-0001-R0002",)


def test_a_qualification_is_recorded_and_starts_its_continuity_clock():
    made = database()
    with transaction(made) as work:
        record_qualification(work, a_qualification())
        assert work.qualifications.lapsed_on(DAY + timedelta(days=100)) == ()
        assert work.qualifications.lapsed_on(DAY + timedelta(days=200)) == (("w-19", "SMAW"),)


def test_using_a_process_pushes_the_lapse_out():
    made = database()
    with transaction(made) as work:
        record_qualification(work, a_qualification())
        record_process_use(work, "w-19", "SMAW", DAY + timedelta(days=150))
        assert work.qualifications.lapsed_on(DAY + timedelta(days=200)) == ()


def test_using_a_process_nobody_is_qualified_on_is_refused():
    made = database()
    with pytest.raises(ConflictError), transaction(made) as work:
        record_process_use(work, "w-99", "SMAW", DAY)


def test_the_same_qualification_twice_is_refused():
    made = database()
    with transaction(made) as work:
        record_qualification(work, a_qualification())
        with pytest.raises(ConflictError):
            record_qualification(work, a_qualification())
