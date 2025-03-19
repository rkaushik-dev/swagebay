"""Jobs, welds, radiographs and what was read off them.

The shape here follows the record rather than the convenience. A job holds
welds, a weld holds radiographs, a radiograph holds indications, and nothing
skips a level. It is tempting to hang indications off the weld, because that is
where the acceptance rules look at them, but an indication was seen on a
particular piece of film by a particular person and losing that link makes the
report undefendable.

Repair rounds live on the weld row rather than in a separate table. A repair is
a round of the same weld, so it is the same identity with a different round
number, and the unique key is on the job, the prefix, the number and the round
together. Filing a repair as a new weld is exactly the thing that has to be
made impossible, and a unique key is the way to make it impossible rather than
merely discouraged.
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
from qu_imager.persistence.types import DecimalColumn, LengthColumn
from qu_imager.quantities.length import Length


class JobRow(Keyed, Base):
    """One job, which is a client, a site and an acceptance code."""

    __tablename__ = "job"
    __table_args__ = (
        UniqueConstraint("year", "serial"),
        CheckConstraint("serial BETWEEN 1 AND 9999", name="serial_in_range"),
        CheckConstraint("year BETWEEN 0 AND 99", name="year_two_digits"),
    )

    year: Mapped[int] = mapped_column(Integer, nullable=False)
    serial: Mapped[int] = mapped_column(Integer, nullable=False)
    client: Mapped[str] = mapped_column(String(120), nullable=False)
    site: Mapped[str] = mapped_column(String(120), nullable=False)
    acceptance_code: Mapped[str] = mapped_column(String(40), nullable=False)
    procedure_number: Mapped[str] = mapped_column(String(40), nullable=False)
    opened_on: Mapped[date] = mapped_column(Date, nullable=False)
    closed_on: Mapped[date | None] = mapped_column(Date, nullable=True)

    welds: Mapped[list[WeldRow]] = relationship(back_populates="job", cascade="all, delete-orphan")


class WeldRow(Keyed, Base):
    """One weld, or one repair round of one weld."""

    __tablename__ = "weld"
    __table_args__ = (
        UniqueConstraint("job_id", "prefix", "number", "repair_round"),
        Index("ix_weld_job_id", "job_id"),
        CheckConstraint("repair_round >= 0", name="repair_round_not_negative"),
        CheckConstraint("wall > 0", name="wall_positive"),
        CheckConstraint("weld_length > 0", name="weld_length_positive"),
    )

    job_id: Mapped[int] = mapped_column(ForeignKey("job.id"), nullable=False)
    prefix: Mapped[str] = mapped_column(String(4), nullable=False)
    number: Mapped[int] = mapped_column(Integer, nullable=False)
    repair_round: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    material_key: Mapped[str] = mapped_column(String(40), nullable=False)
    outside_diameter: Mapped[Length | None] = mapped_column(LengthColumn, nullable=True)
    wall: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    reinforcement: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    weld_length: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    welder: Mapped[str] = mapped_column(String(40), nullable=False)
    welded_on: Mapped[date] = mapped_column(Date, nullable=False)

    job: Mapped[JobRow] = relationship(back_populates="welds")
    radiographs: Mapped[list[RadiographRow]] = relationship(
        back_populates="weld", cascade="all, delete-orphan"
    )


class RadiographRow(Keyed, Base):
    """One image, what it was shot to, and who read it."""

    __tablename__ = "radiograph"
    __table_args__ = (
        UniqueConstraint("number"),
        UniqueConstraint("weld_id", "exposure_index"),
        CheckConstraint("exposure_index >= 1", name="exposure_from_one"),
        # The day log asks for every exposure on a date across every job, and
        # the unread listing asks for the ones with no reader. Both scanned the
        # whole table before these existed, which was fine at four hundred rows
        # and was not at forty thousand.
        Index("ix_radiograph_exposed_at", "exposed_at"),
        Index("ix_radiograph_read_at", "read_at"),
    )

    weld_id: Mapped[int] = mapped_column(ForeignKey("weld.id"), nullable=False)
    number: Mapped[str] = mapped_column(String(40), nullable=False)
    exposure_index: Mapped[int] = mapped_column(Integer, nullable=False)
    exposed_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    technique_reference: Mapped[str] = mapped_column(String(80), nullable=False)
    medium: Mapped[str] = mapped_column(String(40), nullable=False)
    admissible: Mapped[bool] = mapped_column(nullable=False, default=False)
    inadmissible_reason: Mapped[str | None] = mapped_column(String(400), nullable=True)
    density_through_indicator: Mapped[Decimal | None] = mapped_column(DecimalColumn, nullable=True)
    normalised_snr: Mapped[Decimal | None] = mapped_column(DecimalColumn, nullable=True)
    read_by: Mapped[str | None] = mapped_column(String(40), nullable=True)
    read_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    weld: Mapped[WeldRow] = relationship(back_populates="radiographs")
    indications: Mapped[list[IndicationRow]] = relationship(
        back_populates="radiograph", cascade="all, delete-orphan"
    )


class IndicationRow(Keyed, Base):
    """One discontinuity, on one piece of film."""

    __tablename__ = "indication"
    __table_args__ = (
        Index("ix_indication_radiograph_id", "radiograph_id"),
        CheckConstraint("starts_at >= 0", name="starts_on_the_weld"),
        CheckConstraint("length >= 0", name="length_not_negative"),
        CheckConstraint("width >= 0", name="width_not_negative"),
    )

    radiograph_id: Mapped[int] = mapped_column(ForeignKey("radiograph.id"), nullable=False)
    kind: Mapped[str] = mapped_column(String(8), nullable=False)
    starts_at: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    length: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    width: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    depth_fraction: Mapped[Decimal | None] = mapped_column(DecimalColumn, nullable=True)
    clock_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)

    radiograph: Mapped[RadiographRow] = relationship(back_populates="indications")
