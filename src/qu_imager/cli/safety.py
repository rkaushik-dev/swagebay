"""Safety commands, which are the ones run standing next to the truck.

The boundary command takes the load and what is fitted as arguments rather than
reading the inventory, for the same reason the api does: the inventory does not
know whether the collimator went on. It answers the question as asked and the
crew is responsible for asking it correctly, which is the honest division.

The output is two distances and a sentence, because that is what gets read out
over a radio. Anything longer gets shortened by the person reading it and the
part they drop is the part that mattered.
"""

from __future__ import annotations

from decimal import Decimal

import click

from qu_imager.cli.main import cli
from qu_imager.cli.render import table
from qu_imager.quantities.activity import Activity
from qu_imager.quantities.length import Length
from qu_imager.safety.field import BoundaryKind, RadiationField, rope_off
from qu_imager.safety.shielding import Barrier, half_value_layer, tenth_value_layer
from qu_imager.sources.device import collimator, known_collimators
from qu_imager.sources.isotope import isotope

DOWN = Decimal(0)
SIDE = Decimal(180)


@cli.group("safety")
def safety_group() -> None:
    """Boundaries, shielding and the rope."""


@safety_group.command("boundary")
@click.option("--isotope", "isotope_key", default="ir192", show_default=True)
@click.option("--curies", required=True)
@click.option("--collimator", "cup", default=None, help="leave out for an open beam")
@click.option("--barrier", default=None, help="for example lead")
@click.option("--thickness", default=None, help="for example 30 mm")
@click.pass_context
def boundary_command(
    ctx: click.Context,
    isotope_key: str,
    curies: str,
    cup: str | None,
    barrier: str | None,
    thickness: str | None,
) -> None:
    """Print where the rope goes, down the beam and to the sides."""
    barriers: tuple[Barrier, ...] = ()
    if barrier is not None:
        if thickness is None:
            raise click.UsageError("a barrier needs a thickness")
        barriers = (Barrier(barrier, Length.parse(thickness)),)
    field = RadiationField(
        isotope=isotope(isotope_key),
        activity=Activity.from_curie(Decimal(curies)),
        collimator=collimator(cup) if cup is not None else None,
        barriers=barriers,
    )
    rows = [
        [
            kind.value,
            kind.rate.format_mr_per_hour(),
            field.boundary(kind, DOWN).format_m(),
            field.boundary(kind, SIDE).format_m(),
        ]
        for kind in BoundaryKind
    ]
    click.echo(table(["posting", "rate", "down beam", "off beam"], rows, numeric=[1, 2, 3]))
    click.echo("")
    click.echo(rope_off(field).describe())


@safety_group.command("shielding")
@click.option("--isotope", "isotope_key", default="ir192", show_default=True)
@click.option("--material", default="lead", show_default=True)
@click.pass_context
def shielding_command(ctx: click.Context, isotope_key: str, material: str) -> None:
    """Print the half and tenth value layers for one isotope and material."""
    source = isotope(isotope_key)
    half = half_value_layer(source, material)
    tenth = tenth_value_layer(source, material)
    rows = [
        ["half value layer", half.format_mm(2)],
        ["tenth value layer", tenth.format_mm(2)],
        ["ten half layers", (half * 10).format_mm(1)],
    ]
    click.echo(table(["layer", material], rows, numeric=[1]))


@safety_group.command("collimators")
@click.pass_context
def collimators_command(ctx: click.Context) -> None:
    """List the collimators and what each one cuts the side field to."""
    rows = [
        [
            key,
            collimator(key).material,
            f"{collimator(key).aperture_degrees}",
            f"{collimator(key).transmission.normalize()}",
            f"{collimator(key).mass_kg.normalize()}",
        ]
        for key in known_collimators()
    ]
    click.echo(
        table(["cup", "material", "aperture", "transmission", "kg"], rows, numeric=[2, 3, 4])
    )
