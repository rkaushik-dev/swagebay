"""Sources, the devices that carry them, and where they have been.

The relationships here are declared even where nothing reads them, and that is
on purpose. SQLAlchemy orders inserts inside a flush by mapper relationship,
not by foreign key column, so a source and its first leak test added in the
same flush go in whichever order the objects were configured unless there is a
relationship to sort them by. That failure is intermittent, it depends on
import order, and it took an afternoon to find the first time.

Cascade is set to delete orphans on the child collections. A leak test with no
source is not a record, it is a fragment, and leaving them behind makes the
quarterly accountability query wrong in a way nobody notices for months.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from qu_imager.persistence.base import Base, Keyed
from qu_imager.persistence.types import ActivityColumn, DecimalColumn, LengthColumn
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.length import Length


class SourceRow(Keyed, Base):
    """A sealed source as its certificate describes it."""

    __tablename__ = "source"
    __table_args__ = (
        UniqueConstraint("serial"),
        CheckConstraint("assay_activity > 0", name="assay_positive"),
        CheckConstraint("active_diameter > 0", name="pellet_diameter_positive"),
        CheckConstraint("active_length > 0", name="pellet_length_positive"),
    )

    serial: Mapped[str] = mapped_column(String(40), nullable=False)
    isotope_key: Mapped[str] = mapped_column(String(20), nullable=False)
    assay_activity: Mapped[Activity] = mapped_column(ActivityColumn, nullable=False)
    assay_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    manufacturer: Mapped[str] = mapped_column(String(80), nullable=False)
    model: Mapped[str] = mapped_column(String(80), nullable=False)
    active_diameter: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    active_length: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    state: Mapped[str] = mapped_column(String(30), nullable=False, default="in service")
    working_floor: Mapped[Activity] = mapped_column(ActivityColumn, nullable=False)

    leak_tests: Mapped[list[LeakTestRow]] = relationship(
        back_populates="source", cascade="all, delete-orphan"
    )
    trips: Mapped[list[TripRow]] = relationship(
        back_populates="source", cascade="all, delete-orphan"
    )
    sightings: Mapped[list[SightingRow]] = relationship(
        back_populates="source", cascade="all, delete-orphan"
    )


class LeakTestRow(Keyed, Base):
    """One wipe test against one source."""

    __tablename__ = "leak_test"
    __table_args__ = (CheckConstraint("becquerels_removed >= 0", name="removed_not_negative"),)

    source_id: Mapped[int] = mapped_column(ForeignKey("source.id"), nullable=False)
    performed_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    becquerels_removed: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    technician: Mapped[str] = mapped_column(String(40), nullable=False)

    source: Mapped[SourceRow] = relationship(back_populates="leak_tests")


class DeviceRow(Keyed, Base):
    """One physical exposure device the shop owns."""

    __tablename__ = "device"
    __table_args__ = (UniqueConstraint("serial"),)

    serial: Mapped[str] = mapped_column(String(40), nullable=False)
    model_key: Mapped[str] = mapped_column(String(20), nullable=False)

    trips: Mapped[list[TripRow]] = relationship(back_populates="device")


class TripRow(Keyed, Base):
    """One source, out in one device, on one job."""

    __tablename__ = "trip"
    __table_args__ = (
        Index("ix_trip_source_id_came_back", "source_id", "came_back"),
        CheckConstraint("exposures >= 0", name="exposures_not_negative"),
        CheckConstraint(
            "came_back IS NULL OR came_back >= went_out", name="return_after_departure"
        ),
    )

    source_id: Mapped[int] = mapped_column(ForeignKey("source.id"), nullable=False)
    device_id: Mapped[int] = mapped_column(ForeignKey("device.id"), nullable=False)
    crew_badge: Mapped[str] = mapped_column(String(20), nullable=False)
    site: Mapped[str] = mapped_column(String(120), nullable=False)
    went_out: Mapped[date] = mapped_column(Date, nullable=False)
    came_back: Mapped[date | None] = mapped_column(Date, nullable=True)
    exposures: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    source: Mapped[SourceRow] = relationship(back_populates="trips")
    device: Mapped[DeviceRow] = relationship(back_populates="trips")


class SightingRow(Keyed, Base):
    """Somebody laid eyes on a source and signed for it."""

    __tablename__ = "sighting"

    source_id: Mapped[int] = mapped_column(ForeignKey("source.id"), nullable=False)
    seen_on: Mapped[date] = mapped_column(Date, nullable=False)
    seen_by: Mapped[str] = mapped_column(String(40), nullable=False)
    location: Mapped[str] = mapped_column(String(120), nullable=False)

    source: Mapped[SourceRow] = relationship(back_populates="sightings")
