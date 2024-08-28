"""The counter table behind every number that has to be gapless.

A radiograph number cannot have holes in it. A client asked to accept a report
package with radiographs R-0041 through R-0043 and no R-0042 will ask what
happened to R-0042, and the true answer, that a database sequence handed the
number out and then the transaction rolled back, is not an answer anybody
accepts. A missing radiograph is a missing radiograph.

So the counter is a row, not a sequence. Taking a number is an update inside
the same transaction as the work it numbers, and if the work rolls back the
number rolls back with it. That serialises number issuing, which is the cost,
and it is the right cost for something a shop issues a few hundred of a week.

The counter is per scope so that two jobs can number independently, and the
scope is part of the key rather than of the value, which means a scope that has
never been used has no row rather than a row holding zero.
"""

from __future__ import annotations

from sqlalchemy import CheckConstraint, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from qu_imager.persistence.base import Base, Keyed


class CounterRow(Keyed, Base):
    """One counter, for one kind of number in one scope."""

    __tablename__ = "counter"
    __table_args__ = (
        UniqueConstraint("name", "scope"),
        CheckConstraint("next_value >= 1", name="counter_starts_at_one"),
    )

    name: Mapped[str] = mapped_column(String(40), nullable=False)
    scope: Mapped[str] = mapped_column(String(40), nullable=False, default="")
    next_value: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
