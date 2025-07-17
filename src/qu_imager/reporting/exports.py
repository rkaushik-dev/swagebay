"""Writing the package out as csv, which is what the client's system reads.

Two rules and both come from having sent a file that came back.

Every file starts with a header row and the header names are stable. A client
integration keys on them, so renaming a column is a breaking change even though
nothing in this package would notice.

A rate goes out with the count it came from, and goes out blank when the
count is too small to carry a percentage. That is a change to what the
repair_rate column holds and it was made deliberately, because the old
behaviour sent a client four decimal places off a denominator of two.

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

The manifest went in as a function of its own the first time round, because
the package did not carry the files. It does now, so it is a sheet like the
rest and the special case is gone.
"""

from __future__ import annotations

import csv
import io
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass

from qu_imager.errors import ValidationError
from qu_imager.quantities.length import Length
from qu_imager.reporting.package import Package
from qu_imager.reporting.statistics import Rate

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


def _rate(rate: Rate) -> str:
    """A percentage, or nothing at all when the denominator cannot carry one.

    An empty cell is the honest answer to a rate on four welds. A number
    there would be read, and it would be read as a trend.
    """
    return f"{rate.value:.4f}" if rate.is_reportable else ""


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


def _report_rows(package: Package) -> Iterable[Row]:
    for report in package.reports:
        yield [
            report.reference,
            report.item,
            report.requirement,
            str(report.disposition) if report.disposition else "",
            report.concession,
            report.removal,
            report.raised_on.isoformat(),
            report.raised_by,
            report.closed_on.isoformat() if report.closed_on else "",
            report.closed_by,
        ]


def _file_rows(package: Package) -> Iterable[Row]:
    for image in package.files:
        yield [
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
        f"{package.repair_rate().repaired} of {package.repair_rate().shot}",
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
            "repair_rate_basis",
            "radiographs",
            "complete",
        ),
        _summary_rows,
    ),
    Sheet(
        "reports",
        (
            "reference",
            "item",
            "requirement",
            "disposition",
            "concession",
            "removal",
            "raised_on",
            "raised_by",
            "closed_on",
            "closed_by",
        ),
        _report_rows,
    ),
    Sheet(
        "files",
        (
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
        ),
        _file_rows,
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
