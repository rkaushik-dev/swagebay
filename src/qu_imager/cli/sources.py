"""Source commands, which is what the store issues off in the morning.

The fitness command defaults to now, which contradicts what the api does on
purpose. A coordinator using the api is planning. A storeman at the counter
with a crew waiting is asking about right now, and making them type a timestamp
to find out whether the pig can go in the truck would mean nobody runs it.

The history command prints forwards and does not paginate. A source history is
tens of lines, not thousands, and a regulator reading one wants the whole thing
on one page.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal

import click

from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import table, yes_no
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.length import Length
from qu_imager.services.sources import (
    Booking,
    bring_back,
    fitness,
    location,
    register_device,
    register_source,
    retire,
    send_out,
)
from qu_imager.services.wipes import history, record_wipe, wipes_overdue
from qu_imager.sources.isotope import isotope
from qu_imager.sources.sealed import SealedSource, SourceCertificate

TIMESTAMP = ["%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"]
DAY = ["%Y-%m-%d"]


@cli.group("source")
def source_group() -> None:
    """Register sources, book them out and answer for them."""


@source_group.command("register")
@click.argument("serial")
@click.option("--isotope", "isotope_key", required=True)
@click.option("--curies", required=True)
@click.option("--assayed", type=click.DateTime(TIMESTAMP), required=True)
@click.option("--manufacturer", default="QSA", show_default=True)
@click.option("--model", default="A424-9", show_default=True)
@click.option("--pellet-diameter", default="3 mm", show_default=True)
@click.option("--pellet-length", default="3 mm", show_default=True)
@click.option("--floor", default="15", show_default=True, help="working floor in curies")
@click.pass_context
def register_command(
    ctx: click.Context,
    serial: str,
    isotope_key: str,
    curies: str,
    assayed: datetime,
    manufacturer: str,
    model: str,
    pellet_diameter: str,
    pellet_length: str,
    floor: str,
) -> None:
    """Put a source on the inventory."""
    source = SealedSource(
        certificate=SourceCertificate(
            serial=serial,
            isotope=isotope(isotope_key),
            assay_activity=Activity.from_curie(Decimal(curies)),
            assay_at=assayed,
            manufacturer=manufacturer,
            model=model,
            active_diameter=Length.parse(pellet_diameter),
            active_length=Length.parse(pellet_length),
        ),
        working_floor=Activity.from_curie(Decimal(floor)),
    )
    with work_for(ctx) as work:
        register_source(work, source)
    click.echo(f"registered {serial}")


@source_group.command("add-device")
@click.argument("serial")
@click.argument("model_key")
@click.pass_context
def add_device_command(ctx: click.Context, serial: str, model_key: str) -> None:
    """Put an exposure device on the inventory."""
    with work_for(ctx) as work:
        register_device(work, serial, model_key)
    click.echo(f"registered device {serial}")


@source_group.command("list")
@click.pass_context
def list_command(ctx: click.Context) -> None:
    """List the sources in service, with where each one is."""
    with work_for(ctx) as work:
        rows = [
            [
                row.serial,
                row.isotope_key,
                row.assay_activity.format_curie(),
                row.assay_at.date().isoformat(),
                location(work, row.serial),
            ]
            for row in work.sources.in_service()
        ]
    click.echo(table(["serial", "isotope", "assay", "assayed", "where"], rows, numeric=[2]))


@source_group.command("fitness")
@click.argument("serial")
@click.option("--at", type=click.DateTime(TIMESTAMP), default=None)
@click.pass_context
def fitness_command(ctx: click.Context, serial: str, at: datetime | None) -> None:
    """Say whether a source can go out, now or at a stated moment."""
    moment = at or datetime.now()
    with work_for(ctx) as work:
        report = fitness(work, serial, moment)
    click.echo(f"{serial} fit: {yes_no(report.fit)}")
    for reason in report.reasons:
        click.echo(f"  {reason}")


@source_group.command("send-out")
@click.argument("serial")
@click.option("--device", required=True)
@click.option("--crew", required=True)
@click.option("--site", required=True)
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.pass_context
def send_out_command(
    ctx: click.Context, serial: str, device: str, crew: str, site: str, on: datetime
) -> None:
    """Book a source out to a job."""
    with work_for(ctx) as work:
        send_out(work, Booking(serial, device, crew, site, on.date()))
    click.echo(f"{serial} out to {site}")


@source_group.command("bring-back")
@click.argument("serial")
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.option("--exposures", type=int, default=0, show_default=True)
@click.pass_context
def bring_back_command(ctx: click.Context, serial: str, on: datetime, exposures: int) -> None:
    """Book a source back in."""
    with work_for(ctx) as work:
        bring_back(work, serial, on.date(), exposures)
    click.echo(f"{serial} back in the shop")


@source_group.command("wipe")
@click.argument("serial")
@click.option("--at", type=click.DateTime(TIMESTAMP), required=True)
@click.option("--removed", default="0", show_default=True, help="becquerels removed")
@click.option("--by", "technician", required=True)
@click.pass_context
def wipe_command(
    ctx: click.Context, serial: str, at: datetime, removed: str, technician: str
) -> None:
    """Record a leak test."""
    with work_for(ctx) as work:
        result = record_wipe(work, serial, at, Decimal(removed), technician)
    click.echo(result.describe())


@source_group.command("overdue")
@click.option("--on", type=click.DateTime(DAY), required=True)
@click.pass_context
def overdue_command(ctx: click.Context, on: datetime) -> None:
    """List the sources whose leak test has run out."""
    with work_for(ctx) as work:
        serials = wipes_overdue(work, on.date())
    click.echo(table(["serial"], [[serial] for serial in serials]))


@source_group.command("history")
@click.argument("serial")
@click.pass_context
def history_command(ctx: click.Context, serial: str) -> None:
    """Print a source history sheet, oldest first."""
    with work_for(ctx) as work:
        entries = history(work, serial)
    rows = [[e.at.date().isoformat(), e.what, e.detail] for e in entries]
    click.echo(table(["date", "what", "detail"], rows))


@source_group.command("retire")
@click.argument("serial")
@click.pass_context
def retire_command(ctx: click.Context, serial: str) -> None:
    """Take a source out of service."""
    with work_for(ctx) as work:
        retire(work, serial)
    click.echo(f"{serial} awaiting disposal")
