"""Register commands, which are all variations on what is outstanding today.

Each of these is the query somebody runs on a Monday morning. They print
nothing when there is nothing, which is deliberate: a command that prints
"nothing overdue" gets its output piped somewhere and the empty case becomes a
line that has to be filtered out. Silence composes.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

import click

from qu_imager.calibration import Certificate, Instrument
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import table
from qu_imager.safety.incidents import Incident, Occurrence
from qu_imager.services.registers import (
    issue_film,
    notify_incident,
    receive_lot,
    record_calibration,
    record_incident,
    sheets_on_hand,
)
from qu_imager.stock import Lot

DAY = ["%Y-%m-%d"]
TIMESTAMP = ["%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"]


@cli.group("register")
def register_group() -> None:
    """Incidents, calibrations, stock and the archive."""


@register_group.command("incident")
@click.argument("reference")
@click.option("--kind", required=True, help="the occurrence, for example OVEREXPOSURE")
@click.option("--found", type=click.DateTime(TIMESTAMP), required=True)
@click.option("--site", required=True)
@click.option("--by", "reported_by", required=True)
@click.option("--account", required=True)
@click.pass_context
def incident_command(
    ctx: click.Context,
    reference: str,
    kind: str,
    found: datetime,
    site: str,
    reported_by: str,
    account: str,
) -> None:
    """Record an abnormal occurrence and print when it is due."""
    occurrence = Occurrence[kind.upper()]
    entry = Incident(
        reference=reference,
        occurrence=occurrence,
        discovered_at=found,
        site=site,
        reported_by=reported_by,
        account=account,
    )
    with work_for(ctx) as work:
        record_incident(work, entry)
    click.echo(entry.describe())


@register_group.command("notify")
@click.argument("reference")
@click.option("--at", type=click.DateTime(TIMESTAMP), required=True)
@click.pass_context
def notify_command(ctx: click.Context, reference: str, at: datetime) -> None:
    """Record that an incident has been reported."""
    with work_for(ctx) as work:
        notify_incident(work, reference, at)
    click.echo(f"{reference} reported")


@register_group.command("outstanding")
@click.pass_context
def outstanding_command(ctx: click.Context) -> None:
    """List the incidents nobody has reported yet."""
    with work_for(ctx) as work:
        rows = [
            [row.reference, row.occurrence, row.discovered_at.strftime("%Y-%m-%d %H:%M"), row.site]
            for row in work.incidents.unreported()
        ]
    if rows:
        click.echo(table(["reference", "occurrence", "found", "site"], rows))


@register_group.command("calibrate")
@click.argument("serial")
@click.option("--kind", default="SURVEY_METER", show_default=True)
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.option("--due", type=click.DateTime(DAY), required=True)
@click.option("--laboratory", default="NRC", show_default=True)
@click.option("--certificate", default="", help="the laboratory reference")
@click.pass_context
def calibrate_command(
    ctx: click.Context,
    serial: str,
    kind: str,
    on: datetime,
    due: datetime,
    laboratory: str,
    certificate: str,
) -> None:
    """Record a calibration certificate."""
    with work_for(ctx) as work:
        record_calibration(
            work,
            serial,
            Instrument[kind.upper()],
            Certificate(on.date(), due.date(), laboratory, certificate or serial),
        )
    click.echo(f"{serial} calibrated until {due.date().isoformat()}")


@register_group.command("expired")
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.pass_context
def expired_command(ctx: click.Context, on: datetime) -> None:
    """List the instruments out of calibration."""
    with work_for(ctx) as work:
        serials = work.calibrations.expired_on(on.date())
    if serials:
        click.echo(table(["serial"], [[serial] for serial in serials]))


@register_group.command("receive")
@click.argument("lot_number")
@click.option("--film", default="d7", show_default=True)
@click.option("--size", default="4x10", show_default=True)
@click.option("--sheets", type=int, required=True)
@click.option("--received", type=click.DateTime(DAY), required=True)
@click.option("--expires", type=click.DateTime(DAY), required=True)
@click.pass_context
def receive_command(
    ctx: click.Context,
    lot_number: str,
    film: str,
    size: str,
    sheets: int,
    received: datetime,
    expires: datetime,
) -> None:
    """Put a lot of film on the shelf."""
    with work_for(ctx) as work:
        receive_lot(
            work,
            Lot(lot_number, film, size, sheets, received.date(), expires.date()),
        )
    click.echo(f"received {lot_number}, {sheets} sheets")


@register_group.command("issue")
@click.option("--film", default="d7", show_default=True)
@click.option("--size", default="4x10", show_default=True)
@click.option("--sheets", type=int, required=True)
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.pass_context
def issue_command(ctx: click.Context, film: str, size: str, sheets: int, on: datetime) -> None:
    """Take film off the shelf and say which lots it came from."""
    with work_for(ctx) as work:
        taken = issue_film(work, on.date(), film, size, sheets)
        left = sheets_on_hand(work, on.date(), film, size)
    for lot_number, count in taken:
        click.echo(f"{lot_number} {count}")
    click.echo(f"{left} sheets left")


@register_group.command("stock")
@click.option("--film", default="d7", show_default=True)
@click.option("--size", default="4x10", show_default=True)
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.pass_context
def stock_command(ctx: click.Context, film: str, size: str, on: datetime) -> None:
    """List the usable lots, shortest dated first."""
    with work_for(ctx) as work:
        rows = [
            [row.lot_number, str(row.sheets), row.expires_on.isoformat()]
            for row in work.stock.usable_on(on.date(), film, size)
        ]
        expired = work.stock.expired_on(on.date())
    if rows:
        click.echo(table(["lot", "sheets", "expires"], rows, numeric=[1]))
    for lot_number in expired:
        click.echo(f"expired: {lot_number}")


def _decimal(value: str) -> Decimal:
    return Decimal(value)


def _today() -> date:
    return date.today()
