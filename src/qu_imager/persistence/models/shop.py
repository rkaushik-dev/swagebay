"""Source exchanges and the utilisation log, which are the two shop registers.

Both are append only in practice and neither is enforced as such here, because
both get corrected: a utilisation entry written from a memory at the end of the
week gets an exposure count added on Monday, and an exchange gets its shipment
number when the paperwork arrives. The audit trail covers who changed them.

The radiographers on a utilisation entry are a comma separated column rather
than a join table. A badge is looked up by exact match against a list of two or
three, the list is written once and read whole, and a join table here would be
two rows per day per device that nothing ever queries on its own.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import (
    CheckConstraint,
    Date,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed


class ExchangeRow(Keyed, Base):
    """One source swap, on one device, on one day."""

    __tablename__ = "exchange"
    __table_args__ = (
        CheckConstraint("length(device_serial) > 0", name="an_exchange_names_the_device"),
        CheckConstraint("length(performed_by) > 0", name="an_exchange_is_signed"),
        CheckConstraint("length(shipment) > 0", name="an_exchange_names_the_shipment"),
        CheckConstraint(
            "removed_serial IS NOT NULL OR installed_serial IS NOT NULL",
            name="an_exchange_moved_something",
        ),
        CheckConstraint(
            "removed_serial IS NULL OR removed_serial <> installed_serial",
            name="an_exchange_is_not_the_same_source_twice",
        ),
        Index("ix_exchange_device", "device_serial", "performed_on"),
        Index("ix_exchange_shipment", "shipment"),
    )

    device_serial: Mapped[str] = mapped_column(String(40), nullable=False)
    removed_serial: Mapped[str | None] = mapped_column(String(40), nullable=True)
    installed_serial: Mapped[str | None] = mapped_column(String(40), nullable=True)
    performed_on: Mapped[date] = mapped_column(Date, nullable=False)
    performed_by: Mapped[str] = mapped_column(String(20), nullable=False)
    shipment: Mapped[str] = mapped_column(String(60), nullable=False)
    notes: Mapped[str] = mapped_column(String(400), nullable=False, default="")


class UseRow(Keyed, Base):
    """One device, on one day, at one site, with the crew that ran it."""

    __tablename__ = "device_use"
    __table_args__ = (
        UniqueConstraint("device_serial", "used_on", "site"),
        CheckConstraint("length(radiographers) > 0", name="a_use_names_somebody"),
        CheckConstraint("length(site) > 0", name="a_use_names_where"),
        CheckConstraint("exposures >= 0", name="exposures_do_not_go_backwards"),
        Index("ix_device_use_device", "device_serial", "used_on"),
        Index("ix_device_use_source", "source_serial", "used_on"),
        Index("ix_device_use_day", "used_on"),
    )

    device_serial: Mapped[str] = mapped_column(String(40), nullable=False)
    source_serial: Mapped[str] = mapped_column(String(40), nullable=False)
    used_on: Mapped[date] = mapped_column(Date, nullable=False)
    site: Mapped[str] = mapped_column(String(120), nullable=False)
    radiographers: Mapped[str] = mapped_column(Text, nullable=False)
    exposures: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
