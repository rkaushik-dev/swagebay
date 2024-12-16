"""Report commands.

The package command prints the faults first and the summary second, which is
the opposite of the order they are assembled in and the right order to read
them. Somebody running this wants to know whether the job can be sent, and the
answer to that is the fault list.

The export command writes to a directory rather than to standard output,
because there are three files and piping three files down one pipe is not a
thing. It prints the names it wrote so a script can pick them up.
"""

from __future__ import annotations

from pathlib import Path

import click

from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, pairs, table, yes_no
from qu_imager.identity import JobNumber
from qu_imager.reporting.exports import sheets, to_files, to_text
from qu_imager.reporting.package import assemble
from qu_imager.reporting.traceability import broken_chains


@cli.group("report")
def report_group() -> None:
    """Assemble and export the report package."""


@report_group.command("package")
@click.argument("number")
@click.pass_context
def package_command(ctx: click.Context, number: str) -> None:
    """Print the package, faults first."""
    identity = JobNumber.parse(number)
    with work_for(ctx) as work:
        package = assemble(work, identity)
    click.echo(heading(f"report package {identity}"))
    if package.faults:
        click.echo(bullets(list(package.faults)))
        click.echo("")
    click.echo(
        pairs(
            [
                ("client", package.client),
                ("site", package.site),
                ("code", package.code.value),
                ("procedure", package.procedure_number),
                ("welds", str(len(package.welds))),
                ("welds shot", str(package.welds_shot)),
                ("welds repaired", str(package.welds_repaired)),
                ("repair rate", f"{package.repair_rate():.2%}"),
                ("radiographs", str(len(package.radiographs))),
                ("complete", yes_no(package.is_complete)),
            ]
        )
    )


@report_group.command("welds")
@click.argument("number")
@click.pass_context
def welds_command(ctx: click.Context, number: str) -> None:
    """Print the weld summary as a table."""
    with work_for(ctx) as work:
        package = assemble(work, JobNumber.parse(number))
    rows = [
        [
            line.weld,
            line.wall.format_mm(),
            line.welder,
            str(line.radiographs),
            line.disposition,
            " ".join(line.clauses),
        ]
        for line in package.welds
    ]
    click.echo(
        table(["weld", "wall", "welder", "films", "disposition", "clauses"], rows, numeric=[1, 3])
    )


@report_group.command("traceability")
@click.argument("number")
@click.pass_context
def traceability_command(ctx: click.Context, number: str) -> None:
    """Print only the chains that do not hold."""
    identity = JobNumber.parse(number)
    with work_for(ctx) as work:
        broken = broken_chains(work, identity.year, identity.serial)
    if not broken:
        click.echo("every chain holds")
        return
    for chain in broken:
        click.echo(chain.describe())


@report_group.command("export")
@click.argument("number")
@click.option(
    "--into",
    type=click.Path(file_okay=False, path_type=Path),
    default=".",
    show_default=True,
)
@click.pass_context
def export_command(ctx: click.Context, number: str, into: Path) -> None:
    """Write the csv sheets into a directory and print what was written."""
    with work_for(ctx) as work:
        package = assemble(work, JobNumber.parse(number))
    into.mkdir(parents=True, exist_ok=True)
    for name, text in sorted(to_files(package).items()):
        (into / name).write_text(text, encoding="utf-8", newline="")
        click.echo(str(into / name))


@report_group.command("sheet")
@click.argument("number")
@click.argument("which", type=click.Choice(sheets()))
@click.pass_context
def sheet_command(ctx: click.Context, number: str, which: str) -> None:
    """Print one csv sheet to standard output."""
    with work_for(ctx) as work:
        package = assemble(work, JobNumber.parse(number))
    click.echo(to_text(package, which), nl=False)
