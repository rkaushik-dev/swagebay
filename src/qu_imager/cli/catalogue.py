"""Catalogue commands, which answer the questions a shooter asks a wall chart.

Every one of these is a lookup somebody currently does off a laminated card in
the truck, and the cards disagree with each other because they were printed in
different decades. Printing them from the same tables the planner reads means
the card and the plan cannot drift apart.

The isotope command is the one that gets used. It answers what will shoot this
wall, which is the first question of every job, and it answers it softest first
because the softest isotope that covers a wall gives the best contrast.
"""

from __future__ import annotations

import click

from qu_imager.cli.main import cli
from qu_imager.cli.render import plain, table
from qu_imager.exposure.catalogue import all_charts
from qu_imager.imaging.film import film, known_films
from qu_imager.imaging.indicators import WireIndicator, hole_identities, wire_numbers
from qu_imager.materials import Quality, known_materials, material
from qu_imager.quantities.length import Length
from qu_imager.sources.isotope import isotope, isotopes_covering, known_isotopes


@cli.group("catalogue")
def catalogue_group() -> None:
    """The tables the planner reads, printed."""


@catalogue_group.command("isotopes")
@click.option("--covering", default=None, help="steel thickness, for example 20 mm")
@click.pass_context
def isotopes_command(ctx: click.Context, covering: str | None) -> None:
    """List the isotopes, or the ones that cover a wall, softest first."""
    if covering is not None:
        found = isotopes_covering(Length.parse(covering))
    else:
        found = tuple(isotope(key) for key in known_isotopes())
    rows = [
        [
            source.symbol,
            f"{source.half_life_days}",
            f"{source.gamma_constant}",
            source.steel_min.format_mm(0),
            source.steel_max.format_mm(0),
        ]
        for source in found
    ]
    click.echo(
        table(
            ["isotope", "half life d", "R/h at 1 m", "from", "to"],
            rows,
            numeric=[1, 2, 3, 4],
        )
    )


@catalogue_group.command("films")
@click.pass_context
def films_command(ctx: click.Context) -> None:
    """List the film systems and their speeds."""
    rows = [
        [
            key,
            film(key).name,
            str(film(key).film_class),
            plain(film(key).speed_index),
            plain(film(key).granularity),
        ]
        for key in known_films()
    ]
    click.echo(table(["key", "film", "class", "speed", "grain"], rows, numeric=[3, 4]))


@catalogue_group.command("materials")
@click.option("--quality", default="IR192", show_default=True)
@click.pass_context
def materials_command(ctx: click.Context, quality: str) -> None:
    """List the materials and their equivalence factor at one beam quality."""
    wanted = Quality[quality.upper()]
    rows = []
    for key in known_materials():
        entry = material(key)
        try:
            factor = plain(entry.factor_at(wanted))
        except Exception:  # noqa: BLE001 - a hole in the table is a real answer
            factor = "not published"
        rows.append([key, entry.name, str(entry.density_kg_m3), factor])
    click.echo(table(["key", "material", "kg/m3", f"factor at {wanted}"], rows, numeric=[2]))


@catalogue_group.command("indicators")
@click.pass_context
def indicators_command(ctx: click.Context) -> None:
    """List the hole plaque identities and the wire diameters."""
    holes = ", ".join(str(identity) for identity in hole_identities())
    click.echo(f"hole identities  {holes}")
    rows = [
        [WireIndicator(number).iso_label, WireIndicator(number).diameter.format_mm(3)]
        for number in wire_numbers()
    ]
    click.echo("")
    click.echo(table(["wire", "diameter"], rows, numeric=[1]))


@catalogue_group.command("charts")
@click.pass_context
def charts_command(ctx: click.Context) -> None:
    """List the exposure charts the shop has established."""
    rows = [
        [
            chart.key,
            chart.isotope_key,
            chart.material_key,
            chart.film_key,
            plain(chart.density),
            chart.reference_sfd.format_inch(0),
            chart.thinnest.format_mm(1),
            chart.thickest.format_mm(1),
        ]
        for chart in all_charts()
    ]
    click.echo(
        table(
            ["chart", "isotope", "material", "film", "density", "at", "from", "to"],
            rows,
            numeric=[4, 5, 6, 7],
        )
    )
