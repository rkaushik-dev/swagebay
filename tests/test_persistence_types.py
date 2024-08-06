from decimal import Decimal

import pytest
from sqlalchemy import Column, Integer, MetaData, Table, create_engine, insert, select
from sqlalchemy.exc import StatementError

from qu_imager.persistence.base import NAMING_CONVENTION, Base, table_names
from qu_imager.persistence.types import (
    ActivityColumn,
    DecimalColumn,
    DoseColumn,
    DoseRateColumn,
    DurationColumn,
    LengthColumn,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.dose import usv, usv_per_hour
from qu_imager.quantities.duration import seconds
from qu_imager.quantities.length import mm

METADATA = MetaData()

SAMPLE = Table(
    "sample",
    METADATA,
    Column("id", Integer, primary_key=True),
    Column("wall", LengthColumn),
    Column("load", ActivityColumn),
    Column("shot", DurationColumn),
    Column("taken", DoseColumn),
    Column("rate", DoseRateColumn),
    Column("density", DecimalColumn),
)


def engine():
    made = create_engine("sqlite://")
    METADATA.create_all(made)
    return made


def round_trip(**values):
    made = engine()
    with made.begin() as connection:
        connection.execute(insert(SAMPLE).values(id=1, **values))
    with made.connect() as connection:
        return connection.execute(select(SAMPLE).where(SAMPLE.c.id == 1)).mappings().one()


def test_a_length_goes_in_as_micrometres_and_comes_back_a_length():
    row = round_trip(wall=mm("9.53"))
    assert row["wall"] == mm("9.53")
    assert row["wall"].um == 9530


def test_an_activity_survives_the_round_trip_exactly():
    row = round_trip(load=curie(90))
    assert row["load"] == curie(90)
    assert row["load"].curie == Decimal(90)


def test_a_duration_and_a_dose_come_back_as_themselves():
    row = round_trip(shot=seconds(93), taken=usv(500), rate=usv_per_hour(20))
    assert row["shot"] == seconds(93)
    assert row["taken"] == usv(500)
    assert row["rate"] == usv_per_hour(20)


def test_a_decimal_keeps_its_last_digit():
    row = round_trip(density=Decimal("2.55"))
    assert row["density"] == Decimal("2.55")
    assert str(row["density"]) == "2.55"


def test_a_decimal_that_a_float_would_ruin_survives():
    awkward = Decimal("0.1") + Decimal("0.2")
    row = round_trip(density=awkward)
    assert row["density"] == Decimal("0.3")


def test_nulls_stay_null_rather_than_becoming_zero():
    row = round_trip()
    assert row["wall"] is None
    assert row["load"] is None
    assert row["density"] is None


def write_one(**values):
    made = engine()
    with made.begin() as connection:
        connection.execute(insert(SAMPLE).values(id=99, **values))


def test_the_wrong_type_is_refused_rather_than_coerced():
    for column, wrong in (
        ("wall", 9530),
        ("load", 90),
        ("shot", 93),
        ("taken", 500),
        ("rate", 20),
        ("density", 2.55),
    ):
        with pytest.raises(StatementError) as caught:
            write_one(**{column: wrong})
        assert "expected" in str(caught.value)


def test_a_length_column_will_not_take_an_activity():
    with pytest.raises(StatementError) as caught:
        write_one(wall=curie(1))
    assert "expected a Length" in str(caught.value)


def test_the_naming_convention_is_set_on_the_base_metadata():
    assert Base.metadata.naming_convention == NAMING_CONVENTION
    assert Base.metadata.naming_convention["pk"] == "pk_%(table_name)s"


def test_the_table_names_come_back_sorted():
    names = table_names()
    assert names == tuple(sorted(names))
