"""reread columns on the radiograph

Revision ID: cb7bd4de049a
Revises: 775f9dc8b6b7
Create Date: 2026-09-05 07:24:47.718195
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "cb7bd4de049a"
down_revision: str | None = "775f9dc8b6b7"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Autogenerate does not compare check constraints, so the one saying a
    # reread cannot exist without a reading is written out by hand here. It
    # is added in the same batch as the columns so sqlite rebuilds the table
    # once rather than twice.
    with op.batch_alter_table("radiograph", schema=None) as batch_op:
        batch_op.add_column(sa.Column("reread_by", sa.String(length=40), nullable=True))
        batch_op.add_column(sa.Column("reread_at", sa.DateTime(), nullable=True))
        batch_op.add_column(sa.Column("reread_reason", sa.String(length=200), nullable=True))
        batch_op.create_check_constraint(
            "reread_follows_a_reading", "reread_by IS NULL OR read_by IS NOT NULL"
        )


def downgrade() -> None:
    with op.batch_alter_table("radiograph", schema=None) as batch_op:
        batch_op.drop_constraint("reread_follows_a_reading", type_="check")
        batch_op.drop_column("reread_reason")
        batch_op.drop_column("reread_at")
        batch_op.drop_column("reread_by")
