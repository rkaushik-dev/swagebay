"""Non conformance commands, which the coordinator runs and not the crew.

Raise, decide, close, and two listings. The listings are separate commands
rather than flags because the open list is what a Monday meeting works through
and the job list is what goes in front of a client, and those want different
columns.

Close exits non zero when it is refused, so the close out script for a job
stops rather than carrying on and producing a package with an open report
behind it.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from qu_imager.cli.gates import as_day, report
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, table
from qu_imager.errors import ValidationError
from qu_imager.nonconformance import Disposition, Report

DISPOSITIONS = tuple(d.name.lower() for d in Disposition)


def _disposition(key: str) -> Disposition:
    try:
        return Disposition[key.strip().upper().replace(" ", "_").replace("-", "_")]
    except KeyError:
        raise ValidationError(
            "nothing here dispositions like that", given=key, known=", ".join(DISPOSITIONS)
        ) from None


def _rows(reports: tuple[Report, ...], on: date) -> list[list[str]]:
    return [
        [
            r.reference,
            r.job,
            r.item,
            str(r.disposition) if r.disposition else "undecided",
            str(r.days_open(on)),
            "open" if r.is_open else str(r.closed_on),
        ]
        for r in reports
    ]


@cli.group("ncr")
def ncr_group() -> None:
    """Raise and work through non conformance reports."""


@ncr_group.command("raise")
@click.argument("reference")
@click.option("--job", required=True)
@click.option("--item", required=True, help="the weld, spool or component")
@click.option("--requirement", required=True, help="the clause it fails")
@click.option("--account", required=True, help="what is actually wrong")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--by", "badge", required=True)
@click.pass_context
def raise_command(
    ctx: click.Context,
    reference: str,
    job: str,
    item: str,
    requirement: str,
    account: str,
    on: datetime | None,
    badge: str,
) -> None:
    """Raise a report against an item on a job."""
    report = Report(
        reference=reference,
        job=job,
        item=item,
        requirement=requirement,
        account=account,
        raised_on=as_day(on),
        raised_by=badge,
    )
    with work_for(ctx) as work:
        work.nonconformances.raise_report(report)
    click.echo(report.describe())


@ncr_group.command("decide")
@click.argument("reference")
@click.argument("disposition", type=click.Choice(DISPOSITIONS))
@click.option("--concession", default="", help="required for repair and use as is")
@click.option("--removal", default="", help="required for reject")
@click.pass_context
def decide_command(
    ctx: click.Context, reference: str, disposition: str, concession: str, removal: str
) -> None:
    """Set the disposition, with the paperwork it needs."""
    with work_for(ctx) as work:
        report = work.nonconformances.decide(
            reference, _disposition(disposition), concession, removal
        )
    click.echo(report.describe())


@ncr_group.command("close")
@click.argument("reference")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--by", "badge", required=True)
@click.pass_context
def close_command(ctx: click.Context, reference: str, on: datetime | None, badge: str) -> None:
    """Close a report. Somebody other than the author has to do it."""
    day = as_day(on)
    with work_for(ctx) as work:
        report = work.nonconformances.close(reference, day, badge)
    click.echo(report.describe())


@ncr_group.command("open")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def open_command(ctx: click.Context, on: datetime | None) -> None:
    """Every open report, with the overdue ones called out."""
    day = as_day(on)
    with work_for(ctx) as work:
        reports = work.nonconformances.open_reports()
    if not reports:
        click.echo("nothing open")
        return
    click.echo(
        table(
            ["reference", "job", "item", "disposition", "days", "state"],
            _rows(reports, day),
            numeric=[4],
        )
    )
    overdue = [r.reference for r in reports if r.is_overdue(day)]
    if overdue:
        click.echo(heading("overdue"))
        click.echo(bullets(overdue))


@ncr_group.command("job")
@click.argument("job")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def job_command(ctx: click.Context, job: str, on: datetime | None) -> None:
    """Every report on one job, and what is stopping it being signed off."""
    day = as_day(on)
    with work_for(ctx) as work:
        reports = work.nonconformances.for_job(job)
        blocking = work.nonconformances.register_for(job).blocks_closing(job)
    if not reports:
        click.echo(f"nothing raised against {job}")
        return
    click.echo(
        table(
            ["reference", "job", "item", "disposition", "days", "state"],
            _rows(reports, day),
            numeric=[4],
        )
    )
    report("stopping the sign off", (("", blocking),))
