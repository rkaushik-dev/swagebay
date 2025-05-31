"""Writing the package out as csv, which is what the client's system reads.

Two rules and both come from having sent a file that came back.

Every file starts with a header row and the header names are stable. A client
integration keys on them, so renaming a column is a breaking change even though
nothing in this package would notice.

Lengths go out in millimetres to two places and the column name says so. A
column called wall with 10970 in it has been read as millimetres by somebody
every time it has ever been sent, and putting the unit in the name is the only
thing that has ever stopped it.

Line endings are written as they come rather than being forced, and the writer
takes a text stream instead of a path, which is what makes this testable
without a temporary directory and what lets the api stream a response.

A sheet used to be three things in three places: a tuple of column names, a
function that wrote the rows, and an entry in each of three lookups. Adding the
file manifest meant editing four places and the first attempt edited three, so
a sheet is now one Sheet object and the lookups are derived from the one list.
"""

from __future__ import annotations

import csv
import io
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.imaging.store import Manifest
from qu_imager.quantities.length import Length
from qu_imager.reporting.package import Package

Row = Sequence[object]


@dataclass(frozen=True, slots=True)
class Sheet:
    """One csv file: its name, its header, and where its rows come from."""

    name: str
    columns: tuple[str, ...]
    rows: Callable[[Package], Iterable[Row]]

    def write(self, package: Package, stream: io.StringIO) -> None:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(self.columns)
        for row in self.rows(package):
            writer.writerow(list(row))


def _millimetres(value: Length) -> str:
    return f"{value.mm:.2f}"


def _rate(value: Decimal) -> str:
    return f"{value:.4f}"


def _weld_rows(package: Package) -> Iterable[Row]:
    for line in package.welds:
        yield [
            line.weld,
            _millimetres(line.wall),
            line.welder,
            line.radiographs,
            line.disposition,
            " ".join(line.clauses),
        ]


def _radiograph_rows(package: Package) -> Iterable[Row]:
    for line in package.radiographs:
        yield [
            line.number,
            line.weld,
            line.exposure_index,
            line.exposed_on.isoformat(),
            line.technique_reference,
            line.medium,
            "yes" if line.admissible else "no",
            line.read_by or "",
            line.indications,
        ]


def _summary_rows(package: Package) -> Iterable[Row]:
    yield [
        str(package.job),
        package.client,
        package.site,
        package.code.value,
        package.procedure_number,
        package.opened_on.isoformat(),
        package.closed_on.isoformat() if package.closed_on else "",
        len(package.welds),
        package.welds_shot,
        package.welds_repaired,
        _rate(package.repair_rate()),
        len(package.radiographs),
        "yes" if package.is_complete else "no",
    ]


SHEETS = (
    Sheet(
        "welds",
        ("weld", "wall_mm", "welder", "radiographs", "disposition", "clauses"),
        _weld_rows,
    ),
    Sheet(
        "radiographs",
        (
            "radiograph",
            "weld",
            "exposure",
            "exposed_on",
            "technique",
            "medium",
            "admissible",
            "read_by",
            "indications",
        ),
        _radiograph_rows,
    ),
    Sheet(
        "summary",
        (
            "job",
            "client",
            "site",
            "code",
            "procedure",
            "opened_on",
            "closed_on",
            "welds",
            "welds_shot",
            "welds_repaired",
            "repair_rate",
            "radiographs",
            "complete",
        ),
        _summary_rows,
    ),
)

BY_NAME = {sheet.name: sheet for sheet in SHEETS}


def sheet_named(what: str) -> Sheet:
    sheet = BY_NAME.get(what)
    if sheet is None:
        raise ValidationError("no such export", what=what, known=", ".join(sorted(BY_NAME)))
    return sheet


def to_text(package: Package, what: str) -> str:
    """One named sheet of the package, as csv text."""
    stream = io.StringIO()
    sheet_named(what).write(package, stream)
    return stream.getvalue()


def sheets() -> tuple[str, ...]:
    return tuple(sorted(BY_NAME))


def to_files(package: Package) -> dict[str, str]:
    """Every sheet, keyed by the file name it would be saved under."""
    return {f"{str(package.job)}-{what}.csv": to_text(package, what) for what in sheets()}


def columns_of(what: str) -> Iterable[str]:
    return sheet_named(what).columns


MANIFEST_COLUMNS = (
    "filename",
    "radiograph",
    "format",
    "purpose",
    "revision",
    "bit_depth",
    "width",
    "height",
    "bytes",
    "sha256",
)


def manifest_to_text(manifest: Manifest) -> str:
    """The file manifest, which travels with the images and not with the package.

    It is written from the manifest rather than the package because the two are
    assembled from different things and a job can have a complete package and
    no images at all, which is every film job the shop has ever done.
    """
    stream = io.StringIO()
    writer = csv.writer(stream, lineterminator="\n")
    writer.writerow(MANIFEST_COLUMNS)
    for name in sorted(manifest.entries):
        image = manifest.entries[name]
        writer.writerow(
            [
                image.filename,
                image.radiograph_number,
                str(image.image_format),
                str(image.purpose),
                image.revision,
                image.bit_depth,
                image.width,
                image.height,
                image.byte_size,
                image.digest,
            ]
        )
    return stream.getvalue()
