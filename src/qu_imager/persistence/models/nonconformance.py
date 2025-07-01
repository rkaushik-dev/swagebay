"""The non conformance table.

The reference is the natural key and it is unique across the whole register
rather than per job, because that is how it is written on the paper and how the
client refers to it in an email. A per job uniqueness would let two NCR-1s
exist, and the first time somebody forwarded one without the job number the
whole thread would be about the wrong weld.

The disposition is nullable and that is deliberate. A report exists from the
moment somebody raises it, and the gap between raising and deciding is the
thing the register is watching. A default of rework would close that gap by
lying about it.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Index, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed


class NonconformanceRow(Keyed, Base):
    """One report, from raised to closed."""

    __tablename__ = "nonconformance"
    __table_args__ = (
        UniqueConstraint("reference"),
        CheckConstraint("length(reference) > 0", name="a_report_has_a_reference"),
        CheckConstraint("length(raised_by) > 0", name="a_report_is_raised_by_somebody"),
        CheckConstraint(
            "closed_on IS NULL OR closed_on >= raised_on", name="a_report_closes_after_it_opens"
        ),
        CheckConstraint(
            "closed_on IS NULL OR disposition IS NOT NULL",
            name="a_closed_report_has_been_decided",
        ),
        CheckConstraint(
            "closed_by IS NULL OR closed_by <> raised_by",
            name="a_report_is_not_closed_by_its_author",
        ),
        Index("ix_nonconformance_job", "job", "raised_on"),
        Index("ix_nonconformance_open", "closed_on"),
    )

    reference: Mapped[str] = mapped_column(String(40), nullable=False)
    job: Mapped[str] = mapped_column(String(20), nullable=False)
    item: Mapped[str] = mapped_column(String(60), nullable=False)
    requirement: Mapped[str] = mapped_column(String(120), nullable=False)
    account: Mapped[str] = mapped_column(Text, nullable=False)
    raised_on: Mapped[date] = mapped_column(Date, nullable=False)
    raised_by: Mapped[str] = mapped_column(String(20), nullable=False)
    disposition: Mapped[str | None] = mapped_column(String(20), nullable=True)
    concession: Mapped[str] = mapped_column(String(60), nullable=False, default="")
    removal: Mapped[str] = mapped_column(String(60), nullable=False, default="")
    closed_on: Mapped[date | None] = mapped_column(Date, nullable=True)
    closed_by: Mapped[str | None] = mapped_column(String(20), nullable=True)
