"""Device commands, which are the ones run in the yard at seven in the morning.

The inspect command takes defects as item:account pairs so a whole check goes
in as one line. That matters more than it looks: a form that takes four
commands to fill in gets filled in after the truck leaves, and a pre use check
recorded after the truck leaves is not a pre use check.

The clear command is the gate, run against a real device, source and day. It
prints every reason rather than the first, and it exits non zero when the load
cannot go, so it can be the last line of a dispatch script.
"""

from __future__ import annotations

from datetime import datetime

import click

from qu_imager.cli.gates import as_day, report
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, table
from qu_imager.errors import ValidationError
from qu_imager.safety.maintenance import Defect, Item, pre_use, quarterly


def _defect(text: str) -> Defect:
    item, _, account = text.partition(":")
    key = item.strip().upper().replace(" ", "_").replace("-", "_")
    try:
        found = Item[key]
    except KeyError:
        known = ", ".join(sorted(i.name.lower() for i in Item))
        raise ValidationError("nothing here inspects that", item=item, known=known) from None
    if not account.strip():
        raise ValidationError("a defect says what is wrong with it", item=str(found))
    return Defect(found, account.strip())


@cli.group("device")
def device_group() -> None:
    """Inspect devices and clear them to leave."""


@device_group.command("inspect")
@click.argument("serial")
@click.option(
    "--quarterly",
    "is_quarterly",
    is_flag=True,
    help="the strip down, not the yard check",
)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--by", "badge", required=True)
@click.option("--defect", "defects", multiple=True, help="item:what is wrong with it")
@click.option("--note", default="")
@click.pass_context
def inspect_command(
    ctx: click.Context,
    serial: str,
    is_quarterly: bool,
    on: datetime | None,
    badge: str,
    defects: tuple[str, ...],
    note: str,
) -> None:
    """Record an inspection against a device."""
    day = as_day(on)
    raised = tuple(_defect(text) for text in defects)
    build = quarterly if is_quarterly else pre_use
    inspection = build(serial, day, badge, raised, note)
    with work_for(ctx) as work:
        work.maintenance.record(inspection)
    click.echo(inspection.describe())
    if inspection.blocking_defects:
        click.echo(heading("device is out of service"))
        click.echo(bullets([d.describe() for d in inspection.blocking_defects]))
    if inspection.noted_defects:
        click.echo(heading("noted"))
        click.echo(bullets([d.describe() for d in inspection.noted_defects]))


@device_group.command("history")
@click.argument("serial")
@click.pass_context
def device_history_command(ctx: click.Context, serial: str) -> None:
    """Every inspection on one device, oldest first."""
    with work_for(ctx) as work:
        found = work.maintenance.for_device(serial)
        due = work.maintenance.register_for((serial,)).quarterly_due_by(serial)
    if not found:
        click.echo(f"nothing on record for {serial}")
        return
    click.echo(
        table(
            ["when", "kind", "by", "verdict", "defects"],
            [
                [
                    str(i.performed_on),
                    "quarterly" if i.is_quarterly else "pre use",
                    i.performed_by,
                    "passed" if i.passed else "failed",
                    "; ".join(d.describe() for d in i.defects) or "-",
                ]
                for i in found
            ],
        )
    )
    if due is not None:
        click.echo(f"next quarterly due by {due}")


@device_group.command("clear")
@click.argument("serial")
@click.option("--source", "source_serial", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--crew", "lead_badge", default="")
@click.pass_context
def clear_command(
    ctx: click.Context,
    serial: str,
    source_serial: str,
    on: datetime | None,
    lead_badge: str,
) -> None:
    """Ask the gate whether this load can leave the shop today."""
    from qu_imager.services.dispatch import clearance

    day = as_day(on)
    with work_for(ctx) as work:
        found = clearance(work, serial, source_serial, day, (), lead_badge or "yard")
    report(
        f"{serial} with {source_serial} is not clear for {day}",
        (
            ("source", found.source_reasons),
            ("device", found.device_reasons),
            ("crew", found.crew_reasons),
        ),
        clear_message=found.describe(),
    )
