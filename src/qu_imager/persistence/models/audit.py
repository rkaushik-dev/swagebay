"""The audit table, which is the one table nothing updates.

Every other row here can be corrected. This one cannot, and the schema says so
rather than the code, because a rule enforced only in the code is a rule that
lasts until somebody opens a database console.

There is no updated_at, no revision, no soft delete flag. A mistaken entry is
followed by a correcting entry and both stay, which is what a trail means. The
index is on the record it describes rather than on the time, because the
question asked of it is always about one record and never about an hour.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed


class AuditRow(Keyed, Base):
    """One appended entry. Nothing rewrites one of these."""

    __tablename__ = "audit"
    __table_args__ = (
        CheckConstraint("length(actor) > 0", name="audit_names_an_actor"),
        CheckConstraint("length(record) > 0", name="audit_names_a_record"),
        Index("ix_audit_record", "record", "identity", "at"),
        Index("ix_audit_actor", "actor", "at"),
    )

    at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    actor: Mapped[str] = mapped_column(String(20), nullable=False)
    action: Mapped[str] = mapped_column(String(12), nullable=False)
    record: Mapped[str] = mapped_column(String(40), nullable=False)
    identity: Mapped[str] = mapped_column(String(60), nullable=False)
    field_name: Mapped[str] = mapped_column(String(40), nullable=False, default="")
    was: Mapped[str] = mapped_column(Text, nullable=False, default="")
    became: Mapped[str] = mapped_column(Text, nullable=False, default="")
    reason: Mapped[str] = mapped_column(String(200), nullable=False, default="")
