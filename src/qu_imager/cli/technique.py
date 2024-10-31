"""The technique command, which is what gets run before every shot.

The output is a sheet, printed as a label and value block, and then the
warnings underneath it with a blank line between. The blank line matters: a
crew reading this off a phone screen at the side of a road scrolls until the
numbers stop, and warnings that run straight on from the numbers get read as
more numbers.

The compare command prints one row per film with the exposure and the total
source out, which are the two numbers a shooter actually chooses on. It does
not print the geometry, because the geometry does not change between films and
a column of identical values teaches nobody anything.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal

import click

from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, pairs, table, yes_no
from qu_imager.errors import ValidationError
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.quantities.duration import Duration
from qu_imager.services.planning import PlanRequest, compare_films, plan

TIMESTAMP = ["%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"]


@cli.group("technique")
def technique_group() -> None:
    """Plan a shot and compare films."""


@technique_group.command("plan")
@click.argument("number")
@click.argument("weld")
@click.option("--source", "serial", required=True)
@click.option("--film", default="d7", show_default=True)
@click.option("--at", type=click.DateTime(TIMESTAMP), required=True)
@click.option("--shot", default=None, help="leave out to let the planner pick")
@click.option("--film-side", is_flag=True, help="put the indicator on the film side")
@click.option("--window", type=int, default=None, help="clear time in minutes")
@click.pass_context
def plan_command(
    ctx: click.Context,
    number: str,
    weld: str,
    serial: str,
    film: str,
    at: datetime,
    shot: str | None,
    film_side: bool,
    window: int | None,
) -> None:
    """Print a technique sheet."""
    request = PlanRequest(
        weld=WeldId.parse(weld),
        source_serial=serial,
        film_key=film,
        shot_at=at,
        shot=_shot(shot),
        indicator_side=IndicatorSide.FILM if film_side else IndicatorSide.SOURCE,
        window=Duration.from_minutes(window) if window is not None else None,
        density=Decimal("2.0"),
    )
    with work_for(ctx) as work:
        sheet = plan(work, JobNumber.parse(number), request)
    click.echo(heading(f"technique sheet {weld}"))
    click.echo(
        pairs(
            [
                ("joint", sheet.joint.describe()),
                ("shot", str(sheet.shot)),
                ("penetrated", sheet.penetrated.format_mm()),
                ("source to film", sheet.source_to_film.format_mm(0)),
                ("unsharpness", sheet.geometry.geometric_unsharpness.format_mm(3)),
                ("indicator", sheet.indicator.describe()),
                ("exposures", str(sheet.exposures)),
                ("each", sheet.exposure.timer),
                ("source out", sheet.total_source_out.as_timer()),
                ("activity", sheet.exposure.activity.format_curie()),
                ("clean", yes_no(sheet.is_clean)),
            ]
        )
    )
    if sheet.warnings:
        click.echo("")
        click.echo(bullets(list(sheet.warnings)))


@technique_group.command("compare")
@click.argument("number")
@click.argument("weld")
@click.option("--source", "serial", required=True)
@click.option("--films", required=True, help="comma separated film keys")
@click.option("--at", type=click.DateTime(TIMESTAMP), required=True)
@click.option("--shot", default=None)
@click.pass_context
def compare_command(
    ctx: click.Context,
    number: str,
    weld: str,
    serial: str,
    films: str,
    at: datetime,
    shot: str | None,
) -> None:
    """Print the same shot on several films, side by side."""
    keys = tuple(key.strip() for key in films.split(",") if key.strip())
    if not keys:
        raise ValidationError("name at least one film to compare", films=films)
    request = PlanRequest(
        weld=WeldId.parse(weld),
        source_serial=serial,
        film_key=keys[0],
        shot_at=at,
        shot=_shot(shot),
    )
    with work_for(ctx) as work:
        sheets = compare_films(work, JobNumber.parse(number), request, keys)
    rows = [
        [
            key,
            sheets[key].exposure.timer,
            str(sheets[key].exposures),
            sheets[key].total_source_out.as_timer(),
            yes_no(sheets[key].is_clean),
        ]
        for key in keys
    ]
    click.echo(table(["film", "each", "n", "source out", "clean"], rows, numeric=[1, 2, 3]))


def _shot(value: str | None) -> ShotType | None:
    if value is None:
        return None
    for shot in ShotType:
        if shot.value == value or shot.name.lower() == value.lower():
            return shot
    raise ValidationError("no shot type by that name", shot=value)
