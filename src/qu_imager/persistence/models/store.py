"""The table behind the image store.

A stored image hangs off the radiograph rather than off the job, because the
question asked of it is always about one exposure. The file name is unique
across the whole store and not merely within the job, since the name is derived
from the radiograph number and radiograph numbers are already unique. Making
the constraint global means a file misfiled under the wrong job is caught by
the database rather than by whoever notices the count is wrong.

The digest is not indexed. It is looked up by name and compared, never
searched, and an index on a sixty four character column that is never a
predicate is a page cache nobody asked for.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed


class StoredImageRow(Keyed, Base):
    """One file, against the exposure it holds."""

    __tablename__ = "stored_image"
    __table_args__ = (
        UniqueConstraint("filename"),
        UniqueConstraint("radiograph_id", "revision"),
        CheckConstraint("revision >= 0", name="revisions_count_from_zero"),
        CheckConstraint("byte_size > 0", name="an_empty_file_is_not_an_image"),
        CheckConstraint("width > 0 AND height > 0", name="an_image_has_both_dimensions"),
        CheckConstraint("length(digest) = 64", name="a_digest_is_sixty_four_characters"),
        Index("ix_stored_image_radiograph_id", "radiograph_id"),
    )

    radiograph_id: Mapped[int] = mapped_column(ForeignKey("radiograph.id"), nullable=False)
    filename: Mapped[str] = mapped_column(String(80), nullable=False)
    image_format: Mapped[str] = mapped_column(String(8), nullable=False)
    purpose: Mapped[str] = mapped_column(String(10), nullable=False)
    revision: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    bit_depth: Mapped[int] = mapped_column(Integer, nullable=False)
    width: Mapped[int] = mapped_column(Integer, nullable=False)
    height: Mapped[int] = mapped_column(Integer, nullable=False)
    byte_size: Mapped[int] = mapped_column(Integer, nullable=False)
    digest: Mapped[str] = mapped_column(String(64), nullable=False)
    captured_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    ingested_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    ingested_by: Mapped[str] = mapped_column(String(20), nullable=False)
