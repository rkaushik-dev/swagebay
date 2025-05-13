"""Reading and appending the audit trail.

The write side has one method and it takes an Entry, so there is no way to
append a row the domain would have refused. The read side is deliberately
narrow: one record's history, one person's day, and the entries that landed
after a signature. Anything wider than that is a report, and reports are built
from these three.

Ordering is by time and then by id. Two entries written inside one transaction
carry the same timestamp to the second, and without the tiebreak they come back
in whatever order the storage feels like, which makes a field history read
backwards about one time in fifty. That is the kind of defect that is found by
a client and not by a test.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.audit import Action, Entry
from qu_imager.persistence.models.audit import AuditRow


def _to_entry(row: AuditRow) -> Entry:
    return Entry(
        at=row.at,
        actor=row.actor,
        action=Action(row.action),
        record=row.record,
        identity=row.identity,
        field_name=row.field_name,
        was=row.was,
        became=row.became,
        reason=row.reason,
    )


class AuditRepository:
    """Append only, and the queries the trail is actually asked."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def append(self, entry: Entry) -> None:
        self.session.add(
            AuditRow(
                at=entry.at,
                actor=entry.actor,
                action=entry.action.value,
                record=entry.record,
                identity=entry.identity,
                field_name=entry.field_name,
                was=entry.was,
                became=entry.became,
                reason=entry.reason,
            )
        )

    def created(self, at: datetime, actor: str, record: str, identity: str) -> None:
        self.append(Entry(at, actor, Action.CREATED, record, identity))

    def signed(self, at: datetime, actor: str, record: str, identity: str) -> None:
        self.append(Entry(at, actor, Action.SIGNED, record, identity))

    def changed(
        self,
        at: datetime,
        actor: str,
        record: str,
        identity: str,
        field_name: str,
        was: str,
        became: str,
        reason: str = "",
    ) -> None:
        self.append(
            Entry(at, actor, Action.CHANGED, record, identity, field_name, was, became, reason)
        )

    def for_record(self, record: str, identity: str) -> tuple[Entry, ...]:
        rows = self.session.scalars(
            select(AuditRow)
            .where(AuditRow.record == record, AuditRow.identity == identity)
            .order_by(AuditRow.at, AuditRow.id)
        ).all()
        return tuple(_to_entry(row) for row in rows)

    def by_actor(self, actor: str, since: datetime, until: datetime) -> tuple[Entry, ...]:
        rows = self.session.scalars(
            select(AuditRow)
            .where(AuditRow.actor == actor, AuditRow.at >= since, AuditRow.at < until)
            .order_by(AuditRow.at, AuditRow.id)
        ).all()
        return tuple(_to_entry(row) for row in rows)

    def current(self, record: str, identity: str, field_name: str) -> str | None:
        row = self.session.scalars(
            select(AuditRow)
            .where(
                AuditRow.record == record,
                AuditRow.identity == identity,
                AuditRow.field_name == field_name,
                AuditRow.action == Action.CHANGED.value,
            )
            .order_by(AuditRow.at.desc(), AuditRow.id.desc())
            .limit(1)
        ).first()
        return row.became if row is not None else None

    def changed_after_signing(self, record: str, identity: str) -> tuple[Entry, ...]:
        entries = self.for_record(record, identity)
        signed_at = next((e.at for e in entries if e.action is Action.SIGNED), None)
        if signed_at is None:
            return ()
        return tuple(e for e in entries if e.at > signed_at and e.action is not Action.SIGNED)
