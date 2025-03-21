"""The five small registers, which are all the same shape and are not the same table.

Incidents, calibrations, the film archive, film stock and welder qualifications
all look alike: a key, a few columns, a date, and a query that asks what is
overdue. It is tempting to put them in one table with a kind column and a blob
of attributes, and it would be wrong.

Each one has its own constraints and its own foreign keys. A calibration
certificate expires after it was issued. A lot expires after it arrived. A
qualification has a coupon thickness that has to be positive. A single table
with a kind column can express none of those, so every one of them would move
from the schema into the code, where nothing enforces it on the way in.

Five small tables with real constraints beat one wide one with none.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    CheckConstraint,
    Date,
    DateTime,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed
from qu_imager.persistence.types import DecimalColumn, LengthColumn
from qu_imager.quantities.length import Length


class IncidentRow(Keyed, Base):
    """One abnormal occurrence and what is owed because of it."""

    __tablename__ = "incident"
    __table_args__ = (
        UniqueConstraint("reference"),
        CheckConstraint(
            "notified_at IS NULL OR notified_at >= discovered_at", name="reported_after_found"
        ),
        Index("ix_incident_discovered_at", "discovered_at"),
    )

    reference: Mapped[str] = mapped_column(String(40), nullable=False)
    occurrence: Mapped[str] = mapped_column(String(60), nullable=False)
    discovered_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    site: Mapped[str] = mapped_column(String(120), nullable=False)
    reported_by: Mapped[str] = mapped_column(String(40), nullable=False)
    account: Mapped[str] = mapped_column(String(2000), nullable=False)
    notified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class CalibrationRow(Keyed, Base):
    """One calibration certificate against one instrument."""

    __tablename__ = "calibration"
    __table_args__ = (
        CheckConstraint("due_on > calibrated_on", name="expires_after_issue"),
        Index("ix_calibration_serial_due", "serial", "due_on"),
    )

    serial: Mapped[str] = mapped_column(String(40), nullable=False)
    instrument_kind: Mapped[str] = mapped_column(String(40), nullable=False)
    calibrated_on: Mapped[date] = mapped_column(Date, nullable=False)
    due_on: Mapped[date] = mapped_column(Date, nullable=False)
    laboratory: Mapped[str] = mapped_column(String(120), nullable=False)
    certificate_reference: Mapped[str] = mapped_column(String(60), nullable=False)


class FiledRow(Keyed, Base):
    """One radiograph in the filing cabinet."""

    __tablename__ = "filed"
    __table_args__ = (
        UniqueConstraint("radiograph"),
        Index("ix_filed_job", "job"),
    )

    radiograph: Mapped[str] = mapped_column(String(40), nullable=False)
    job: Mapped[str] = mapped_column(String(20), nullable=False)
    filed_on: Mapped[date] = mapped_column(Date, nullable=False)
    retention: Mapped[str] = mapped_column(String(40), nullable=False)
    site: Mapped[str | None] = mapped_column(String(120), nullable=True)
    cabinet: Mapped[str | None] = mapped_column(String(40), nullable=True)
    box: Mapped[str | None] = mapped_column(String(40), nullable=True)


class LotRow(Keyed, Base):
    """One batch of film on the shelf."""

    __tablename__ = "lot"
    __table_args__ = (
        UniqueConstraint("lot_number"),
        CheckConstraint("sheets >= 0", name="sheets_not_negative"),
        CheckConstraint("expires_on > received_on", name="expires_after_arrival"),
        Index("ix_lot_film_size_expiry", "film_key", "size", "expires_on"),
    )

    lot_number: Mapped[str] = mapped_column(String(40), nullable=False)
    film_key: Mapped[str] = mapped_column(String(20), nullable=False)
    size: Mapped[str] = mapped_column(String(20), nullable=False)
    sheets: Mapped[int] = mapped_column(Integer, nullable=False)
    received_on: Mapped[date] = mapped_column(Date, nullable=False)
    expires_on: Mapped[date] = mapped_column(Date, nullable=False)


class QualificationRow(Keyed, Base):
    """One welder, one process, one test weld."""

    __tablename__ = "qualification"
    __table_args__ = (
        UniqueConstraint("welder", "process", "qualified_on"),
        CheckConstraint("coupon > 0", name="coupon_positive"),
        Index("ix_qualification_welder", "welder"),
    )

    welder: Mapped[str] = mapped_column(String(40), nullable=False)
    process: Mapped[str] = mapped_column(String(20), nullable=False)
    position: Mapped[str] = mapped_column(String(10), nullable=False)
    coupon: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    material_group: Mapped[str] = mapped_column(String(20), nullable=False)
    qualified_on: Mapped[date] = mapped_column(Date, nullable=False)
    last_used_on: Mapped[date] = mapped_column(Date, nullable=False)


class ControlStripRow(Keyed, Base):
    """One processor control strip."""

    __tablename__ = "control_strip"
    __table_args__ = (
        UniqueConstraint("processor", "read_on"),
        CheckConstraint("speed > 0 AND contrast > 0 AND fog > 0", name="readings_positive"),
    )

    processor: Mapped[str] = mapped_column(String(40), nullable=False)
    read_on: Mapped[date] = mapped_column(Date, nullable=False)
    speed: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    contrast: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    fog: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    operator: Mapped[str] = mapped_column(String(40), nullable=False)
