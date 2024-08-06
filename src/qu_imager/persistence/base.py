"""The declarative base and the naming convention every constraint gets.

Named constraints are not a nicety. Alembic generates a downgrade by dropping
constraints by name, and on a database where the names were left to the engine
those names differ between sqlite and postgres, so a migration that runs in the
test suite fails on the server. Setting the convention once here means every
constraint has a deterministic name from the first migration onward.

The base also fixes what a primary key looks like. Everything in this schema is
keyed on an integer surrogate, and the domain identifier, which is a job number
or a weld identifier or a source serial, is a unique column beside it. That is
deliberate. Those identifiers are real and meaningful and they also get
corrected, and a foreign key onto a corrected identifier is a bad afternoon.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, Integer, MetaData
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    """Every table in the schema hangs off this."""

    metadata = MetaData(naming_convention=NAMING_CONVENTION)


class Keyed:
    """A surrogate primary key, which every table here has."""

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)


class Stamped:
    """When the row was written. Not when the thing happened."""

    recorded_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)


def table_names() -> tuple[str, ...]:
    return tuple(sorted(Base.metadata.tables))
