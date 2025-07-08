"""Job and weld commands.

The listing commands print a table and the write commands print one line
naming what they did. That split is deliberate. Anything a person reads goes in
a table. Anything a script reads is one line with the identifier on it, so
piping the output through cut works and nobody has to parse a table.

Dates default to today and can be overridden. Every command that writes takes
the date it happened rather than assuming the moment it is typed, because the
paperwork is entered the morning after the night shift and dating it today is
how a job ends up with welds shot before they were welded.
"""

from __future__ import annotations

from datetime import datetime

import click

from qu_imager.cli.gates import as_day
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import pairs, table, yes_no
from qu_imager.identity import JobNumber
from qu_imager.naming import code_named
from qu_imager.services.jobs import NewJob, close_job, open_job, outstanding


@cli.group("job")
def job_group() -> None:
    """Open, list and close jobs."""


@job_group.command("open")
@click.option("--client", required=True)
@click.option("--site", required=True)
@click.option("--code", default="API 1104", show_default=True)
@click.option("--procedure", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def open_command(
    ctx: click.Context,
    client: str,
    site: str,
    code: str,
    procedure: str,
    on: datetime | None,
) -> None:
    """Open a job and print its number."""
    opened = as_day(on)
    with work_for(ctx) as work:
        number = open_job(
            work,
            NewJob(
                client=client,
                site=site,
                code=code_named(code),
                procedure_number=procedure,
                opened_on=opened,
            ),
        )
    click.echo(f"opened {number}")


@job_group.command("list")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def list_command(ctx: click.Context, on: datetime | None) -> None:
    """List the jobs open on a day."""
    day = as_day(on)
    with work_for(ctx) as work:
        rows = [
            [
                str(JobNumber(row.year, row.serial)),
                row.client,
                row.site,
                row.acceptance_code,
                str(len(row.welds)),
            ]
            for row in work.jobs.open_on(day)
        ]
    click.echo(table(["job", "client", "site", "code", "welds"], rows, numeric=[4]))


@job_group.command("show")
@click.argument("number")
@click.pass_context
def show_command(ctx: click.Context, number: str) -> None:
    """Print one job and what is outstanding on it."""
    identity = JobNumber.parse(number)
    with work_for(ctx) as work:
        row = work.jobs.get(identity)
        pending = outstanding(work, identity)
        block = pairs(
            [
                ("job", str(identity)),
                ("client", row.client),
                ("site", row.site),
                ("code", row.acceptance_code),
                ("procedure", row.procedure_number),
                ("opened", row.opened_on.isoformat()),
                ("closed", row.closed_on.isoformat() if row.closed_on else "no"),
                ("welds", str(len(row.welds))),
                ("clear", yes_no(not pending)),
            ]
        )
    click.echo(block)
    for item in pending:
        click.echo(f"  outstanding: {item}")


@job_group.command("close")
@click.argument("number")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def close_command(ctx: click.Context, number: str, on: datetime | None) -> None:
    """Close a job, refusing while anything is outstanding."""
    identity = JobNumber.parse(number)
    day = as_day(on)
    with work_for(ctx) as work:
        close_job(work, identity, day)
    click.echo(f"closed {identity}")
