"""Reference tables: the catalogues, written down so a record can point at one.

These look redundant. The isotopes are already in the code, so why put them in
the database as well.

Because a record has to keep pointing at what it pointed at. A technique sheet
from two years ago used an equivalence factor that has since been revised, and
the sheet is only defensible if the factor it actually used can be recovered.
The code carries today's values. These tables carry the values as of the row
that references them, and the row references a version.

So the reference tables are append only. A revised isotope is a new row with a
later effective date, never an update in place, and the constraint that makes
that stick is the unique key on key plus revision rather than on key alone.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal

from sqlalchemy import CheckConstraint, Date, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed
from qu_imager.persistence.types import DecimalColumn, LengthColumn
from qu_imager.quantities.length import Length


class MaterialRow(Keyed, Base):
    """A material and its density, as of a revision."""

    __tablename__ = "material"
    __table_args__ = (
        UniqueConstraint("key", "revision"),
        CheckConstraint("revision >= 0", name="revision_not_negative"),
        CheckConstraint("density_kg_m3 > 0", name="density_positive"),
    )

    key: Mapped[str] = mapped_column(String(40), nullable=False)
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    density_kg_m3: Mapped[int] = mapped_column(Integer, nullable=False)
    ferrous: Mapped[bool] = mapped_column(nullable=False, default=False)
    effective_on: Mapped[date] = mapped_column(Date, nullable=False)


class IsotopeRow(Keyed, Base):
    """An isotope and the four numbers planning needs, as of a revision."""

    __tablename__ = "isotope"
    __table_args__ = (
        UniqueConstraint("key", "revision"),
        CheckConstraint("half_life_days > 0", name="half_life_positive"),
        CheckConstraint("energy_kev_low <= energy_kev_high", name="energy_band_ordered"),
    )

    key: Mapped[str] = mapped_column(String(20), nullable=False)
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    symbol: Mapped[str] = mapped_column(String(20), nullable=False)
    half_life_days: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    gamma_constant: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    energy_kev_low: Mapped[int] = mapped_column(Integer, nullable=False)
    energy_kev_high: Mapped[int] = mapped_column(Integer, nullable=False)
    steel_min: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    steel_max: Mapped[Length] = mapped_column(LengthColumn, nullable=False)
    effective_on: Mapped[date] = mapped_column(Date, nullable=False)


class FilmRow(Keyed, Base):
    """A film system and its speed, as of a revision."""

    __tablename__ = "film"
    __table_args__ = (
        UniqueConstraint("key", "revision"),
        CheckConstraint("speed_index > 0", name="speed_positive"),
    )

    key: Mapped[str] = mapped_column(String(20), nullable=False)
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    manufacturer: Mapped[str] = mapped_column(String(80), nullable=False)
    film_class: Mapped[str] = mapped_column(String(10), nullable=False)
    speed_index: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    granularity: Mapped[Decimal] = mapped_column(DecimalColumn, nullable=False)
    effective_on: Mapped[date] = mapped_column(Date, nullable=False)
