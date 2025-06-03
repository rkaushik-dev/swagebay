"""Image store commands, run from the van as often as from the office.

Ingest takes a real path because the digest has to be taken off the bytes that
are actually on the disk. Anything that let a caller pass a digest in would be
a checker that trusts the thing it is checking.

Verify is the reason this exists. It walks a directory, hashes what it finds,
and prints the three faults apart: gone, unexpected, and changed. It exits
non zero when the store does not match, so it can be the last line of a
copy script and stop a truck leaving with a bad card.
"""

from __future__ import annotations

import pathlib
from datetime import datetime

import click

from qu_imager.cli.main import cli, work_for
from qu_imager.cli.render import bullets, heading, table
from qu_imager.errors import ValidationError
from qu_imager.identity import JobNumber
from qu_imager.imaging.store import (
    Format,
    Purpose,
    StoredImage,
    expected_name,
    faults,
)

CHUNK = 1024 * 1024
EXIT_STORE_MISMATCH = 4


def _digest_of_file(path: pathlib.Path) -> tuple[str, int]:
    """Hash a file a megabyte at a time, since a panel image is not small."""
    import hashlib

    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while True:
            block = handle.read(CHUNK)
            if not block:
                break
            size += len(block)
            digest.update(block)
    return digest.hexdigest(), size


def _format_from(path: pathlib.Path) -> Format:
    suffix = path.suffix.lower().lstrip(".")
    for candidate in Format:
        if candidate.suffix == suffix:
            return candidate
    raise ValidationError("nothing here reads that kind of file", suffix=suffix)


@cli.group("store")
def store_group() -> None:
    """Ingest, list and verify stored images."""


@store_group.command("ingest")
@click.argument("radiograph")
@click.argument("path", type=click.Path(exists=True, dir_okay=False, path_type=pathlib.Path))
@click.option("--width", type=click.IntRange(min=1), required=True)
@click.option("--height", type=click.IntRange(min=1), required=True)
@click.option("--bits", type=click.IntRange(min=1), default=16, show_default=True)
@click.option("--revision", type=click.IntRange(min=0), default=0, show_default=True)
@click.option("--viewing", is_flag=True, help="a viewing copy, not the record")
@click.option("--by", "badge", required=True)
@click.pass_context
def ingest_command(
    ctx: click.Context,
    radiograph: str,
    path: pathlib.Path,
    width: int,
    height: int,
    bits: int,
    revision: int,
    viewing: bool,
    badge: str,
) -> None:
    """Take a file into the store under the name its exposure gives it."""
    image_format = _format_from(path)
    digest, size = _digest_of_file(path)
    purpose = Purpose.VIEWING if viewing else Purpose.RECORD
    image = StoredImage(
        radiograph_number=radiograph,
        filename=expected_name(radiograph, image_format, revision),
        image_format=image_format,
        bit_depth=bits,
        width=width,
        height=height,
        byte_size=size,
        digest=digest,
        captured_at=datetime.fromtimestamp(path.stat().st_mtime),
        purpose=purpose,
    )
    wrong = faults(image)
    with work_for(ctx) as work:
        work.store.ingest(image, datetime.now(), badge)
    click.echo(f"stored {image.filename} {size} bytes {digest[:12]}")
    if wrong:
        click.echo(bullets(wrong))


@store_group.command("list")
@click.argument("number")
@click.pass_context
def store_list_command(ctx: click.Context, number: str) -> None:
    """Every file a job has produced."""
    with work_for(ctx) as work:
        manifest = work.store.manifest_for(JobNumber.parse(number))
        rows = [
            [
                image.filename,
                image.radiograph_number,
                str(image.image_format),
                str(image.purpose),
                str(image.bit_depth),
                str(image.byte_size),
                image.digest[:12],
            ]
            for _, image in sorted(manifest.entries.items())
        ]
        without = manifest.radiographs_without_a_record_copy()
    if not rows:
        click.echo(f"no files stored against {number}")
        return
    click.echo(
        table(
            ["file", "radiograph", "format", "purpose", "bits", "bytes", "sha256"],
            rows,
            numeric=[4, 5],
        )
    )
    if without:
        click.echo(heading("no record copy"))
        click.echo(bullets(list(without)))


@store_group.command("verify")
@click.argument("number")
@click.argument("directory", type=click.Path(exists=True, file_okay=False, path_type=pathlib.Path))
@click.pass_context
def verify_command(ctx: click.Context, number: str, directory: pathlib.Path) -> None:
    """Hash what is on the disk and compare it to the manifest."""
    seen = {}
    for path in sorted(directory.iterdir()):
        if path.is_file():
            seen[path.name] = _digest_of_file(path)[0]
    with work_for(ctx) as work:
        manifest = work.store.manifest_for(JobNumber.parse(number))
    found = manifest.verify(seen)
    click.echo(f"{len(manifest)} in the manifest, {len(seen)} on the disk")
    click.echo(found.describe())
    for label, names in (
        ("missing", found.missing),
        ("not in the manifest", found.unexpected),
        ("changed since ingest", found.altered),
    ):
        if names:
            click.echo(heading(label))
            click.echo(bullets(list(names)))
    if not found.is_clean:
        raise SystemExit(EXIT_STORE_MISMATCH)
