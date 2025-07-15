"""The morning command, run once before anybody gets in a truck.

It prints the sheet in the order the sheet is meant to be worked through and
exits non zero when anything stops work today, so it can be the first line of
the yard script. Things due this week and reports running late do not stop the
day, so they do not stop the script either.
"""

from __future__ import annotations

from datetime import datetime

import click

from qu_imager.cli.gates import as_day, report
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, table
from qu_imager.services.morning import films_waiting, loads_ready, sheet_for


@cli.command("morning")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--loads", is_flag=True, help="also list the pairings that could go out")
@click.pass_context
def morning_command(ctx: click.Context, on: datetime | None, loads: bool) -> None:
    """Everything outstanding, in the order it has to be dealt with."""
    day = as_day(on)
    with work_for(ctx) as work:
        sheet = sheet_for(work, day)
        waiting = films_waiting(work, day)
        pairings = loads_ready(work, day) if loads else ()

    if waiting:
        click.echo(heading("films waiting to be read"))
        click.echo(bullets(list(waiting)))
    if loads:
        click.echo(heading("ready to go out"))
        if pairings:
            click.echo(table(["device", "source"], [[d, s] for d, s in pairings]))
        else:
            click.echo("nothing is cleared to leave the shop")

    report(
        f"outstanding on {day}",
        (
            ("stops work today", [line.describe() for line in sheet.stops_today]),
            ("due this week", [line.describe() for line in sheet.stops_this_week]),
            ("running late", [line.describe() for line in sheet.running_late]),
        ),
        clear_message=sheet.describe(),
    )
