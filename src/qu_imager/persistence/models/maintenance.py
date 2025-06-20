"""Inspection records, and the defects raised on them.

Two tables rather than one, because a defect is a row with two facts on it and
squeezing them into a text column on the inspection means the day somebody asks
which devices have ever had a drive cable fault, the answer is a like query
against prose.

The items checked do go in one column, as a comma separated list of item keys.
That is the one place a list is denormalised here, and it is deliberate: the
list is written once, read whole, and never filtered on. A join table for it
would be three hundred rows a quarter that nothing ever queries.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, Date, ForeignKey, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from qu_imager.persistence.base import Base, Keyed


class InspectionRow(Keyed, Base):
    """One inspection of one device."""

    __tablename__ = "inspection"
    __table_args__ = (
        CheckConstraint("length(device_serial) > 0", name="an_inspection_names_the_device"),
        CheckConstraint("length(performed_by) > 0", name="an_inspection_is_signed"),
        CheckConstraint("length(items_checked) > 0", name="an_inspection_checked_something"),
        Index("ix_inspection_device", "device_serial", "performed_on"),
    )

    device_serial: Mapped[str] = mapped_column(String(40), nullable=False)
    performed_on: Mapped[date] = mapped_column(Date, nullable=False)
    performed_by: Mapped[str] = mapped_column(String(20), nullable=False)
    is_quarterly: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    items_checked: Mapped[str] = mapped_column(Text, nullable=False)
    notes: Mapped[str] = mapped_column(String(400), nullable=False, default="")

    defects: Mapped[list[DefectRow]] = relationship(
        back_populates="inspection", cascade="all, delete-orphan"
    )


class DefectRow(Keyed, Base):
    """One thing wrong, on one item, on one inspection."""

    __tablename__ = "defect"
    __table_args__ = (
        CheckConstraint("length(account) > 0", name="a_defect_says_what_is_wrong"),
        Index("ix_defect_inspection_id", "inspection_id"),
        Index("ix_defect_item", "item_key"),
    )

    inspection_id: Mapped[int] = mapped_column(ForeignKey("inspection.id"), nullable=False)
    item_key: Mapped[str] = mapped_column(String(30), nullable=False)
    account: Mapped[str] = mapped_column(String(400), nullable=False)

    inspection: Mapped[InspectionRow] = relationship(back_populates="defects")
