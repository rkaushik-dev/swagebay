"""Surveys, dose and the instruments people carry.

The dose table is the one worth thinking about. It is an append only ledger of
entries rather than a running total per person, and the running total is a sum
over it. That is slower and it is the only defensible shape. A running total
column is a number somebody can correct, and a corrected dose total with no
entry behind the correction is exactly what an inspector will ask about.

Survey points hang off a survey rather than being free standing readings. A
reading with no survey has no instrument, no signature and no stage, which
makes it a number somebody wrote on their hand.

The surface reading flag is a column rather than being inferred from the
distance, because what makes a reading a surface reading is that the surveyor
put the probe on the device, and no distance in a database can establish that.
"""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import (
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from qu_imager.persistence.base import Base, Keyed
from qu_imager.persistence.types import DoseColumn, DoseRateColumn, LengthColumn
from qu_imager.quantities.dose import Dose, DoseRate
from qu_imager.quantities.length import Length


class SurveyRow(Keyed, Base):
    """One walk round with a meter."""

    __tablename__ = "survey"
    __table_args__ = (CheckConstraint("stage IN ('before', 'after')", name="stage_known"),)

    radiograph_number: Mapped[str | None] = mapped_column(String(40), nullable=True)
    stage: Mapped[str] = mapped_column(String(10), nullable=False)
    performed_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    performed_by: Mapped[str] = mapped_column(String(40), nullable=False)
    instrument: Mapped[str] = mapped_column(String(80), nullable=False)
    calibration_due: Mapped[datetime] = mapped_column(DateTime, nullable=False)

    points: Mapped[list[SurveyPointRow]] = relationship(
        back_populates="survey", cascade="all, delete-orphan"
    )


class SurveyPointRow(Keyed, Base):
    """One reading on one survey."""

    __tablename__ = "survey_point"
    __table_args__ = (CheckConstraint("distance > 0", name="point_has_a_distance"),)

    survey_id: Mapped[int] = mapped_column(ForeignKey("survey.id"), nullable=False)
    label: Mapped[str] = mapped_column(String(80), nullable=False)
    distance: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    bearing_degrees: Mapped[int] = mapped_column(nullable=False, default=0)
    reading: Mapped[DoseRate] = mapped_column(DoseRateColumn, nullable=False)
    at_device_surface: Mapped[bool] = mapped_column(nullable=False, default=False)

    survey: Mapped[SurveyRow] = relationship(back_populates="points")


class DoseEntryRow(Keyed, Base):
    """One dose taken by one person, appended and never corrected in place."""

    __tablename__ = "dose_entry"
    __table_args__ = (CheckConstraint("taken >= 0", name="dose_not_negative"),)

    badge: Mapped[str] = mapped_column(String(20), nullable=False)
    year: Mapped[int] = mapped_column(nullable=False)
    taken: Mapped[Dose] = mapped_column(DoseColumn, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    source_of_entry: Mapped[str] = mapped_column(String(40), nullable=False)


class DosimeterRow(Keyed, Base):
    """One instrument issued to one person."""

    __tablename__ = "dosimeter"
    __table_args__ = (
        UniqueConstraint("serial"),
        CheckConstraint(
            "kind IN ('badge', 'direct reading', 'alarming ratemeter')", name="kind_known"
        ),
    )

    serial: Mapped[str] = mapped_column(String(40), nullable=False)
    kind: Mapped[str] = mapped_column(String(30), nullable=False)
    badge: Mapped[str] = mapped_column(String(20), nullable=False)
    worker_class: Mapped[str] = mapped_column(String(30), nullable=False)
    calibration_due: Mapped[date | None] = mapped_column(Date, nullable=True)
