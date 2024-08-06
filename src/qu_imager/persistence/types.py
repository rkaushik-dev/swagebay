"""Column types that put the value objects in the database without flattening them.

A length is whole micrometres and an activity is whole becquerels, so both go
into an integer column and come back out as the type they went in as. That is
the whole trick and it is worth doing properly, because the alternative is a
schema full of columns called wall_mm and pellet_um where the unit lives in the
name and nothing checks it.

Decimals are the awkward case. SQLite has no decimal type and stores anything
numeric as a float, which loses the last digit of a density reading. So every
decimal here is stored as text and parsed back, which is exact, sorts wrongly
in raw SQL, and is the right trade because nothing sorts on a density.

Every one of these refuses rather than coercing. A row with a null in a column
declared not null is a schema fault, and a type decorator that quietly returns
zero for it turns a schema fault into a wrong number on a report.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any

from sqlalchemy import Integer, Text
from sqlalchemy.engine import Dialect
from sqlalchemy.types import TypeDecorator

from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import Dose, DoseRate
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length


class LengthColumn(TypeDecorator[Length]):
    """A length, as whole micrometres."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Length | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        if not isinstance(value, Length):
            raise TypeError(f"expected a Length, got {type(value).__name__}")
        return value.um

    def process_result_value(self, value: Any, dialect: Dialect) -> Length | None:
        if value is None:
            return None
        return Length(int(value))


class ActivityColumn(TypeDecorator[Activity]):
    """An activity, as whole becquerels."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Activity | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        if not isinstance(value, Activity):
            raise TypeError(f"expected an Activity, got {type(value).__name__}")
        return value.bq

    def process_result_value(self, value: Any, dialect: Dialect) -> Activity | None:
        if value is None:
            return None
        return Activity(int(value))


class DurationColumn(TypeDecorator[Duration]):
    """A duration, as whole milliseconds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Duration | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        if not isinstance(value, Duration):
            raise TypeError(f"expected a Duration, got {type(value).__name__}")
        return value.ms

    def process_result_value(self, value: Any, dialect: Dialect) -> Duration | None:
        if value is None:
            return None
        return Duration(int(value))


class DoseColumn(TypeDecorator[Dose]):
    """A dose, as whole nanosieverts."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Dose | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        if not isinstance(value, Dose):
            raise TypeError(f"expected a Dose, got {type(value).__name__}")
        return value.nsv

    def process_result_value(self, value: Any, dialect: Dialect) -> Dose | None:
        if value is None:
            return None
        return Dose(int(value))


class DoseRateColumn(TypeDecorator[DoseRate]):
    """A dose rate, as whole nanosieverts an hour."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: DoseRate | None, dialect: Dialect) -> int | None:
        if value is None:
            return None
        if not isinstance(value, DoseRate):
            raise TypeError(f"expected a DoseRate, got {type(value).__name__}")
        return value.nsv_per_hour

    def process_result_value(self, value: Any, dialect: Dialect) -> DoseRate | None:
        if value is None:
            return None
        return DoseRate(int(value))


class DecimalColumn(TypeDecorator[Decimal]):
    """A decimal, as text, because a float would round the last digit off."""

    impl = Text
    cache_ok = True

    def process_bind_param(self, value: Decimal | None, dialect: Dialect) -> str | None:
        if value is None:
            return None
        if not isinstance(value, Decimal):
            raise TypeError(f"expected a Decimal, got {type(value).__name__}")
        return str(value)

    def process_result_value(self, value: Any, dialect: Dialect) -> Decimal | None:
        if value is None:
            return None
        return Decimal(str(value))
