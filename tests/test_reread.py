from datetime import date, datetime, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.length import mm
from qu_imager.services.reading import (
    ReadingRecord,
    RereadRequest,
    disputed,
    readers_of,
    record_reading,
    record_reread,
)
from test_reading_service import prepared, reader

from conftest import database

READ = datetime(2024, 9, 25, 8, 0)
AGAIN = READ + timedelta(days=3)
SLAG = (Indication(Kind.ESI, mm(100), mm(20), mm(1)),)
POROSITY = (Indication(Kind.P, mm(100), mm(3), mm(3)),)


def first_reading(work, found=SLAG):
    job, numbers = prepared(work)
    record_reading(work, ReadingRecord(numbers[0], reader(), READ, found))
    return job, numbers[0]


def test_a_reread_is_stored_beside_the_first_reading():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        stored = work.welds.radiograph(number)
        assert stored.read_by == "0142"
        assert stored.reread_by == "0201"
        assert stored.reread_at == AGAIN


def test_the_second_readers_call_is_the_one_that_counts():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        stored = work.welds.radiograph(number)
        assert [i.kind for i in stored.indications] == ["P"]


def test_what_the_first_reader_said_is_on_the_trail():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        history = work.audit.for_record("radiograph", number)
        assert len(history) == 1
        assert history[0].was.startswith("ESI 20.0 mm")
        assert history[0].reason == "client asked"


def test_a_reread_that_agrees_is_recorded_as_a_signature_not_a_change():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "second look", SLAG))
        history = work.audit.for_record("radiograph", number)
        assert [str(e.action) for e in history] == ["signed"]


def test_a_reread_of_a_film_nobody_read_is_refused():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        with pytest.raises(ConflictError):
            record_reread(work, RereadRequest(numbers[0], reader("0201"), AGAIN, "why", SLAG))


def test_a_second_reread_is_refused():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        with pytest.raises(ConflictError):
            record_reread(work, RereadRequest(number, reader("0303"), AGAIN, "again", SLAG))


def test_the_first_reader_may_not_reread_their_own_film():
    with transaction(database()) as work:
        job, number = first_reading(work)
        with pytest.raises(ConflictError):
            record_reread(work, RereadRequest(number, reader("0142"), AGAIN, "why", POROSITY))


def test_a_reread_dated_before_the_reading_is_refused():
    with transaction(database()) as work:
        job, number = first_reading(work)
        earlier = READ - timedelta(hours=1)
        with pytest.raises(ConflictError):
            record_reread(work, RereadRequest(number, reader("0201"), earlier, "why", POROSITY))


def test_a_reader_whose_eyes_have_lapsed_may_not_reread():
    with transaction(database()) as work:
        job, number = first_reading(work)
        stale = reader("0201", vision=date(2022, 1, 1))
        with pytest.raises(ConflictError):
            record_reread(work, RereadRequest(number, stale, AGAIN, "why", POROSITY))


def test_a_reread_with_no_reason_is_refused():
    with pytest.raises(ValidationError):
        RereadRequest("24-0001-R0001", reader("0201"), AGAIN, "   ", POROSITY)


def test_a_reread_that_names_no_film_is_refused():
    with pytest.raises(ValidationError):
        RereadRequest("  ", reader("0201"), AGAIN, "why", POROSITY)


def test_both_readers_come_back_in_the_order_they_read():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        assert readers_of(work, number) == ("0142", "0201")


def test_a_film_with_one_reader_lists_one():
    with transaction(database()) as work:
        job, number = first_reading(work)
        assert readers_of(work, number) == ("0142",)


def test_a_reread_film_shows_up_as_disputed():
    with transaction(database()) as work:
        job, number = first_reading(work)
        assert disputed(work, job) == ()
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "client asked", POROSITY))
        assert disputed(work, job) == (number,)


def test_clearing_the_call_is_recorded_as_a_change_to_nothing():
    with transaction(database()) as work:
        job, number = first_reading(work)
        record_reread(work, RereadRequest(number, reader("0201"), AGAIN, "no such call", ()))
        history = work.audit.for_record("radiograph", number)
        assert history[0].became == "nothing"
        assert work.welds.radiograph(number).indications == []
