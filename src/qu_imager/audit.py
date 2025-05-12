"""The audit trail, which answers who changed what and when.

Everything else in this package records what happened in the world. This
records what happened to the record, which is a different thing and the one a
dispute turns on. A weld disposition that says accept is worth very little on
its own. A weld disposition that says accept, entered by this badge at this
time, having previously said repair and been changed by that badge, is worth
the argument.

Three rules make it useful rather than decorative.

Entries are appended and never edited, so the trail cannot be tidied.

The old value is on the entry as well as the new one, because a trail that says
only what a field became cannot show what it was without replaying every entry
from the beginning, and replaying is what nobody does under time pressure.

Nothing is recorded without an actor. A change with no badge on it is a change
nobody can be asked about, and the trail exists to make somebody askable.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

from qu_imager.errors import ValidationError


class Action(Enum):
    CREATED = "created"
    CHANGED = "changed"
    DELETED = "deleted"
    SIGNED = "signed"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Entry:
    """One change to one field of one record."""

    at: datetime
    actor: str
    action: Action
    record: str
    identity: str
    field_name: str = ""
    was: str = ""
    became: str = ""
    reason: str = ""

    def __post_init__(self) -> None:
        if not self.actor.strip():
            raise ValidationError("a change is made by somebody")
        if not self.record.strip() or not self.identity.strip():
            raise ValidationError("a change names the record it changed")
        if self.action is Action.CHANGED and not self.field_name.strip():
            raise ValidationError("a change names the field it changed")
        if self.action is Action.CHANGED and self.was == self.became:
            raise ValidationError(
                "a change that changed nothing is not a change",
                field=self.field_name,
                value=self.was,
            )

    def describe(self) -> str:
        stamp = self.at.strftime("%Y-%m-%d %H:%M")
        if self.action is Action.CHANGED:
            body = f"{self.field_name} {self.was or 'nothing'} to {self.became or 'nothing'}"
        else:
            body = str(self.action)
        note = f" ({self.reason})" if self.reason.strip() else ""
        return f"{stamp} {self.actor} {self.record} {self.identity} {body}{note}"


@dataclass(slots=True)
class Trail:
    """Every entry, appended and never edited."""

    entries: list[Entry] = field(default_factory=list)

    def append(self, entry: Entry) -> None:
        self.entries.append(entry)

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
        """One record's history, oldest first, which is how it reads."""
        found = [e for e in self.entries if e.record == record and e.identity == identity]
        return tuple(sorted(found, key=lambda e: e.at))

    def by_actor(self, actor: str) -> tuple[Entry, ...]:
        return tuple(e for e in self.entries if e.actor == actor)

    def field_history(self, record: str, identity: str, field_name: str) -> tuple[str, ...]:
        """What one field has been, in order, without replaying the whole trail."""
        entries = [
            e
            for e in self.for_record(record, identity)
            if e.action is Action.CHANGED and e.field_name == field_name
        ]
        if not entries:
            return ()
        return (entries[0].was, *(e.became for e in entries))

    def current(self, record: str, identity: str, field_name: str) -> str | None:
        history = self.field_history(record, identity, field_name)
        return history[-1] if history else None

    def changed_after_signing(self, record: str, identity: str) -> tuple[Entry, ...]:
        """Changes made after somebody signed, which is what a dispute is about."""
        entries = self.for_record(record, identity)
        signed_at = next((e.at for e in entries if e.action is Action.SIGNED), None)
        if signed_at is None:
            return ()
        return tuple(e for e in entries if e.at > signed_at and e.action is not Action.SIGNED)
