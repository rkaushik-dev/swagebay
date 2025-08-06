"""What the value object columns do when something else is handed to them.

Each of these columns exists to stop a plain integer being written where a
quantity belongs, and every one of them raises on the way in. None of that had
a test, because every caller in the package passes the right type, which is
exactly the condition under which a guard quietly stops working.

The identity guards are here for the same reason. The parsers are covered from
every direction; the constructors are what an import script calls, and an
import script is where an out of range number actually comes from.
"""

import pytest

from qu_imager.errors import ValidationError
from qu_imager.identity import JobNumber, WeldId
from qu_imager.persistence.types import (
    ActivityColumn,
    DoseColumn,
    DoseRateColumn,
    DurationColumn,
    LengthColumn,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.dose import usv, usv_per_hour
from qu_imager.quantities.duration import seconds
from qu_imager.quantities.length import mm

COLUMNS = (
    (LengthColumn(), mm(10), "Length"),
    (ActivityColumn(), curie(90), "Activity"),
    (DurationColumn(), seconds(30), "Duration"),
    (DoseColumn(), usv(1000), "Dose"),
    (DoseRateColumn(), usv_per_hour(20), "DoseRate"),
)


@pytest.mark.parametrize(("column", "value", "name"), COLUMNS)
def test_a_quantity_goes_in_as_a_whole_number(column, value, name):
    stored = column.process_bind_param(value, None)
    assert isinstance(stored, int)
    assert column.process_result_value(stored, None) == value


@pytest.mark.parametrize(("column", "value", "name"), COLUMNS)
def test_nothing_goes_in_as_nothing(column, value, name):
    assert column.process_bind_param(None, None) is None
    assert column.process_result_value(None, None) is None


@pytest.mark.parametrize(("column", "value", "name"), COLUMNS)
def test_a_bare_integer_is_refused_by_name(column, value, name):
    with pytest.raises(TypeError) as caught:
        column.process_bind_param(1000, None)
    assert name in str(caught.value)


@pytest.mark.parametrize(("column", "value", "name"), COLUMNS)
def test_the_wrong_quantity_is_refused_too(column, value, name):
    other = curie(1) if name != "Activity" else mm(1)
    with pytest.raises(TypeError):
        column.process_bind_param(other, None)


def test_a_job_year_outside_two_digits_is_refused():
    with pytest.raises(ValidationError) as caught:
        JobNumber(year=100, serial=1)
    assert caught.value.context["year"] == 100
    with pytest.raises(ValidationError):
        JobNumber(year=-1, serial=1)


def test_a_job_serial_outside_four_digits_is_refused():
    with pytest.raises(ValidationError) as caught:
        JobNumber(year=24, serial=0)
    assert caught.value.context["serial"] == 0
    with pytest.raises(ValidationError):
        JobNumber(year=24, serial=10000)


def test_a_weld_prefix_nothing_knows_about_is_refused():
    with pytest.raises(ValidationError) as caught:
        WeldId(prefix="ZZ", number=104)
    assert caught.value.context["prefix"] == "ZZ"


def test_a_weld_number_outside_five_digits_is_refused():
    with pytest.raises(ValidationError):
        WeldId(prefix="GW", number=0)
    with pytest.raises(ValidationError):
        WeldId(prefix="GW", number=100000)


def test_a_repair_round_outside_two_digits_is_refused():
    with pytest.raises(ValidationError) as caught:
        WeldId(prefix="GW", number=104, repair=100)
    assert caught.value.context["repair"] == 100
    with pytest.raises(ValidationError):
        WeldId(prefix="GW", number=104, repair=-1)
