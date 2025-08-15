"""Shop commands: swapping a source and logging a day's use.

Exchange goes through the service rather than the repository, so the same rules
that refuse a load in the domain refuse it here. There is no way to write an
exchange row from this command line that the domain would not accept, and that
is the point of having it.

The gaps command is the one worth running weekly. It compares the trips against
the utilisation log and prints the days a device was out with nothing written
down, and it exits non zero, because a gap is the one thing on this register
that gets harder to fix the longer it is left.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from qu_imager.cli.gates import as_day, report
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import table
from qu_imager.errors import ValidationError
from qu_imager.safety.utilisation import Use
from qu_imager.services.exchanges import record_exchange, record_use, unlogged_days
from qu_imager.sources.exchange import Exchange


@cli.group("shop")
def shop_group() -> None:
    """Source exchanges and the utilisation log."""


@shop_group.command("exchange")
@click.argument("device")
@click.option("--out", "removed", default=None, help="the source coming out")
@click.option("--in", "installed", default=None, help="the source going in")
@click.option("--shipment", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--by", "badge", required=True)
@click.option("--note", default="")
@click.pass_context
def exchange_command(
    ctx: click.Context,
    device: str,
    removed: str | None,
    installed: str | None,
    shipment: str,
    on: datetime | None,
    badge: str,
    note: str,
) -> None:
    """Swap what is in a device, with the rules running."""
    exchange = Exchange(
        device_serial=device,
        removed_serial=removed,
        installed_serial=installed,
        performed_on=as_day(on),
        performed_by=badge,
        shipment=shipment,
        notes=note,
    )
    with work_for(ctx) as work:
        record_exchange(work, exchange)
    click.echo(exchange.describe())


@shop_group.command("loading")
@click.pass_context
def loading_command(ctx: click.Context) -> None:
    """Which source is in which device, replayed from the exchanges."""
    with work_for(ctx) as work:
        held = work.shop.loading().held
    if not held:
        click.echo("no device is holding a source")
        return
    click.echo(table(["device", "source"], [[d, s] for d, s in sorted(held.items())]))


@shop_group.command("use")
@click.argument("device")
@click.option("--source", "source_serial", required=True)
@click.option("--site", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--by", "badges", multiple=True, required=True, help="one per radiographer")
@click.option("--exposures", type=click.IntRange(min=0), default=0, show_default=True)
@click.pass_context
def use_command(
    ctx: click.Context,
    device: str,
    source_serial: str,
    site: str,
    on: datetime | None,
    badges: tuple[str, ...],
    exposures: int,
) -> None:
    """Log a day's use of one device."""
    use = Use(
        used_on=as_day(on),
        device_serial=device,
        source_serial=source_serial,
        site=site,
        radiographers=tuple(badges),
        exposures=exposures,
    )
    with work_for(ctx) as work:
        record_use(work, use)
    click.echo(use.describe())


@shop_group.command("exposures")
@click.argument("device")
@click.option("--site", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--add", "extra", type=click.IntRange(min=0), required=True)
@click.pass_context
def exposures_command(
    ctx: click.Context, device: str, site: str, on: datetime | None, extra: int
) -> None:
    """Top up a count written before the films were counted."""
    with work_for(ctx) as work:
        use = work.shop.add_exposures(device, as_day(on), site, extra)
    click.echo(use.describe())


@shop_group.command("gaps")
@click.option("--since", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--until", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def gaps_command(ctx: click.Context, since: datetime, until: datetime | None) -> None:
    """Days a device was out with nothing in the utilisation log."""
    start = since.date()
    end = until.date() if until is not None else date.today()
    if end < start:
        raise ValidationError("that window ends before it starts", since=str(start))
    with work_for(ctx) as work:
        missing = unlogged_days(work, start, end)
    report(
        f"utilisation gaps from {start} to {end}",
        (("", [f"{serial} on {day}" for serial, day in missing]),),
        clear_message=f"nothing missing between {start} and {end}",
    )
