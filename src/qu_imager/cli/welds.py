"""Weld commands.

A weld is described the way the line list describes it: a wall, a diameter and
a cap, each as text with its unit in it. Leaving the diameter out means plate,
which is the same convention the joint type uses, so a caller who knows one
knows the other.
"""

from __future__ import annotations

from datetime import datetime

import click

from qu_imager.cli.gates import as_day
from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import table
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.identity import JobNumber, WeldId
from qu_imager.materials import material
from qu_imager.quantities.length import Length
from qu_imager.services.jobs import NewWeld, add_weld


@cli.group("weld")
def weld_group() -> None:
    """Add and list welds."""


@weld_group.command("add")
@click.argument("number")
@click.argument("weld")
@click.option("--material", "material_key", default="carbon_steel", show_default=True)
@click.option("--wall", required=True, help="for example 10.97 mm or 0.375 in")
@click.option("--diameter", default=None, help="leave out for plate")
@click.option("--cap", default="0 mm", show_default=True)
@click.option("--length", "weld_length", required=True)
@click.option("--welder", required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_context
def add_command(
    ctx: click.Context,
    number: str,
    weld: str,
    material_key: str,
    wall: str,
    diameter: str | None,
    cap: str,
    weld_length: str,
    welder: str,
    on: datetime | None,
) -> None:
    """Add a weld to a job."""
    identity = JobNumber.parse(number)
    welded = as_day(on)
    joint = Joint(
        form=JointForm.PLATE_BUTT if diameter is None else JointForm.PIPE_GIRTH,
        wall=Length.parse(wall),
        material=material(material_key),
        outside_diameter=Length.parse(diameter) if diameter is not None else None,
        reinforcement=Length.parse(cap),
    )
    with work_for(ctx) as work:
        add_weld(
            work,
            identity,
            NewWeld(
                weld=WeldId.parse(weld),
                joint=joint,
                weld_length=Length.parse(weld_length),
                welder=welder,
                welded_on=welded,
            ),
        )
    click.echo(f"added {weld} to {identity}")


@weld_group.command("list")
@click.argument("number")
@click.pass_context
def weld_list_command(ctx: click.Context, number: str) -> None:
    """List the welds on a job."""
    identity = JobNumber.parse(number)
    with work_for(ctx) as work:
        job = work.jobs.get(identity)
        rows = [
            [
                str(WeldId(row.prefix, row.number, row.repair_round)),
                row.material_key,
                row.wall.format_mm(),
                row.outside_diameter.format_mm() if row.outside_diameter else "plate",
                row.welder,
                str(len(row.radiographs)),
            ]
            for row in sorted(job.welds, key=lambda w: (w.number, w.repair_round))
        ]
    click.echo(
        table(
            ["weld", "material", "wall", "od", "welder", "films"],
            rows,
            numeric=[2, 3, 5],
        )
    )
