"""The doctor command, which reads and never writes.

It exits non zero when it finds anything, so it can run nightly and wake
somebody up. The findings are grouped by check rather than listed flat,
because one bad import produces forty findings from one check and a flat list
of forty makes that look like forty problems.
"""

from __future__ import annotations

import click

from qu_imager.cli.gates import report
from qu_imager.cli.main import cli, work_for
from qu_imager.services.doctor import check_names, examine


@cli.command("doctor")
@click.option(
    "--check",
    "only",
    multiple=True,
    type=click.Choice(check_names()),
    help="run just these, rather than all of them",
)
@click.pass_context
def doctor_command(ctx: click.Context, only: tuple[str, ...]) -> None:
    """Look for records that cannot be true at the same time."""
    with work_for(ctx) as work:
        found = examine(work, only)
    grouped = found.by_check()
    report(
        found.describe(),
        [
            (name, [f"{f.subject} {f.account}" for f in grouped.get(name, ())])
            for name in found.checks_run
        ],
        clear_message=found.describe(),
    )
