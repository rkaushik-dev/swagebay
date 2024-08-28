import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from qu_imager.errors import ValidationError
from qu_imager.persistence.base import Base
from qu_imager.persistence.models import CounterRow
from qu_imager.persistence.numbering import (
    RADIOGRAPH,
    Numbering,
    format_radiograph,
    format_report,
)


def engine():
    made = create_engine("sqlite://")
    Base.metadata.create_all(made)
    return made


def session(made=None):
    return Session(made or engine())


def test_the_first_number_issued_is_one():
    with session() as db:
        assert Numbering(db).take(RADIOGRAPH, "24-0417") == 1


def test_numbers_come_out_in_order_with_no_holes():
    with session() as db:
        counter = Numbering(db)
        issued = [counter.take(RADIOGRAPH, "24-0417") for _ in range(5)]
        assert issued == [1, 2, 3, 4, 5]


def test_two_scopes_number_independently():
    with session() as db:
        counter = Numbering(db)
        assert counter.take(RADIOGRAPH, "24-0417") == 1
        assert counter.take(RADIOGRAPH, "24-0418") == 1
        assert counter.take(RADIOGRAPH, "24-0417") == 2


def test_two_counters_in_one_scope_are_separate_too():
    with session() as db:
        counter = Numbering(db)
        assert counter.take("radiograph", "24-0417") == 1
        assert counter.take("report", "24-0417") == 1


def test_peeking_does_not_take():
    with session() as db:
        counter = Numbering(db)
        assert counter.peek(RADIOGRAPH, "24-0417") == 1
        assert counter.peek(RADIOGRAPH, "24-0417") == 1
        counter.take(RADIOGRAPH, "24-0417")
        assert counter.peek(RADIOGRAPH, "24-0417") == 2


def test_a_scope_never_used_has_no_row_at_all():
    with session() as db:
        counter = Numbering(db)
        assert counter.peek(RADIOGRAPH, "24-9999") == 1
        assert db.scalars(select(CounterRow)).all() == []


def test_a_rolled_back_transaction_takes_the_number_back_with_it():
    made = engine()
    with session(made) as db:
        Numbering(db).take(RADIOGRAPH, "24-0417")
        db.commit()
    with session(made) as db:
        assert Numbering(db).take(RADIOGRAPH, "24-0417") == 2
        db.rollback()
    with session(made) as db:
        assert Numbering(db).take(RADIOGRAPH, "24-0417") == 2


def test_a_committed_number_stays_taken():
    made = engine()
    with session(made) as db:
        assert Numbering(db).take(RADIOGRAPH, "24-0417") == 1
        db.commit()
    with session(made) as db:
        assert Numbering(db).peek(RADIOGRAPH, "24-0417") == 2


def test_a_counter_needs_a_name():
    with session() as db, pytest.raises(ValidationError):
        Numbering(db).take("   ")


def test_a_radiograph_number_reads_the_way_the_envelope_is_written():
    assert format_radiograph("24-0417", 42) == "24-0417-R0042"
    assert format_radiograph("24-0417", 1) == "24-0417-R0001"
    assert format_radiograph("24-0417", 10000) == "24-0417-R10000"


def test_a_report_number_has_its_own_shape():
    assert format_report("24-0417", 3) == "24-0417-RPT003"


def test_a_number_below_one_cannot_be_formatted():
    with pytest.raises(ValidationError):
        format_radiograph("24-0417", 0)
    with pytest.raises(ValidationError):
        format_report("24-0417", -1)


def test_taking_a_formatted_number_advances_the_counter():
    with session() as db:
        counter = Numbering(db)
        assert counter.take_radiograph_number("24-0417") == "24-0417-R0001"
        assert counter.take_radiograph_number("24-0417") == "24-0417-R0002"
        assert counter.take_report_number("24-0417") == "24-0417-RPT001"


def test_the_counter_row_is_created_once_and_then_updated():
    with session() as db:
        counter = Numbering(db)
        for _ in range(4):
            counter.take(RADIOGRAPH, "24-0417")
        rows = db.scalars(select(CounterRow)).all()
        assert len(rows) == 1
        assert rows[0].next_value == 5
