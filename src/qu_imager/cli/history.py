"""Reading the audit trail from a terminal.

This exists because the people who need the trail are not the people with a
browser open. A reader disputing a call, or a supervisor answering a client on
the phone, wants one line per change with the old value on it, and they want it
without joining two tables in their head.

Three commands, and they are deliberately not one command with a mode flag. The
history of one record, one person's day, and the changes made after somebody
signed are three different questions with three different shapes of answer, and
a flag that changes the columns is a flag nobody remembers.
"""

from __future__ import annotations

from datetime import datetime, timedelta

import click

from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import heading, table

RECORDS = ("radiograph", "weld", "job", "source", "survey")


def _rows(entries: tuple, with_actor: bool = True) -> list[list[str]]:
    rows = []
    for entry in entries:
        row = [entry.at.strftime("%Y-%m-%d %H:%M")]
        if with_actor:
            row.append(entry.actor)
        row.extend(
            [
                str(entry.action),
                entry.field_name or "-",
                entry.was or "-",
                entry.became or "-",
                entry.reason or "-",
            ]
        )
        rows.append(row)
    return rows


@cli.group("history")
def history_group() -> None:
    """Read the audit trail."""


@history_group.command("record")
@click.argument("record", type=click.Choice(RECORDS))
@click.argument("identity")
@click.pass_context
def record_command(ctx: click.Context, record: str, identity: str) -> None:
    """Every change to one record, oldest first."""
    with work_for(ctx) as work:
        entries = work.audit.for_record(record, identity)
    if not entries:
        click.echo(f"nothing recorded against {record} {identity}")
        return
    click.echo(heading(f"{record} {identity}"))
    click.echo(
        table(
            ["when", "who", "what", "field", "was", "became", "why"],
            _rows(entries),
        )
    )


@history_group.command("actor")
@click.argument("badge")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--days", type=click.IntRange(min=1), default=1, show_default=True)
@click.pass_context
def actor_command(ctx: click.Context, badge: str, on: datetime, days: int) -> None:
    """What one badge changed, over one day or a few."""
    until = on + timedelta(days=days)
    with work_for(ctx) as work:
        entries = work.audit.by_actor(badge, on, until)
    if not entries:
        click.echo(f"{badge} changed nothing between {on.date()} and {until.date()}")
        return
    click.echo(heading(f"{badge} from {on.date()} to {until.date()}"))
    click.echo(
        table(
            ["when", "what", "field", "was", "became", "why"],
            _rows(entries, with_actor=False),
        )
    )


@history_group.command("after-signing")
@click.argument("record", type=click.Choice(RECORDS))
@click.argument("identity")
@click.pass_context
def after_signing_command(ctx: click.Context, record: str, identity: str) -> None:
    """Changes made after the record was signed. Exits 4 when there are any."""
    with work_for(ctx) as work:
        entries = work.audit.changed_after_signing(record, identity)
    if not entries:
        click.echo(f"nothing changed on {record} {identity} after it was signed")
        return
    click.echo(heading(f"{record} {identity} changed after signing"))
    click.echo(
        table(
            ["when", "who", "what", "field", "was", "became", "why"],
            _rows(entries),
        )
    )
    raise SystemExit(4)
