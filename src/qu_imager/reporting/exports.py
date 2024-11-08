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
"""

from __future__ import annotations

import csv
import io
from collections.abc import Iterable
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.quantities.length import Length
from qu_imager.reporting.package import Package

WELD_COLUMNS = (
    "weld",
    "wall_mm",
    "welder",
    "radiographs",
    "disposition",
    "clauses",
)

RADIOGRAPH_COLUMNS = (
    "radiograph",
    "weld",
    "exposure",
    "exposed_on",
    "technique",
    "medium",
    "admissible",
    "read_by",
    "indications",
)

SUMMARY_COLUMNS = (
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
)


def _millimetres(value: Length) -> str:
    return f"{value.mm:.2f}"


def write_welds(package: Package, stream: io.StringIO) -> None:
    writer = csv.writer(stream, lineterminator="\n")
    writer.writerow(WELD_COLUMNS)
    for line in package.welds:
        writer.writerow(
            [
                line.weld,
                _millimetres(line.wall),
                line.welder,
                line.radiographs,
                line.disposition,
                " ".join(line.clauses),
            ]
        )


def write_radiographs(package: Package, stream: io.StringIO) -> None:
    writer = csv.writer(stream, lineterminator="\n")
    writer.writerow(RADIOGRAPH_COLUMNS)
    for line in package.radiographs:
        writer.writerow(
            [
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
        )


def write_summary(package: Package, stream: io.StringIO) -> None:
    writer = csv.writer(stream, lineterminator="\n")
    writer.writerow(SUMMARY_COLUMNS)
    writer.writerow(
        [
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
    )


def _rate(value: Decimal) -> str:
    return f"{value:.4f}"


def to_text(package: Package, what: str) -> str:
    """One named sheet of the package, as csv text."""
    writers = {
        "welds": write_welds,
        "radiographs": write_radiographs,
        "summary": write_summary,
    }
    if what not in writers:
        raise ValidationError("no such export", what=what, known=", ".join(sorted(writers)))
    stream = io.StringIO()
    writers[what](package, stream)
    return stream.getvalue()


def sheets() -> tuple[str, ...]:
    return ("radiographs", "summary", "welds")


def to_files(package: Package) -> dict[str, str]:
    """Every sheet, keyed by the file name it would be saved under."""
    return {f"{str(package.job)}-{what}.csv": to_text(package, what) for what in sheets()}


def columns_of(what: str) -> Iterable[str]:
    return {
        "welds": WELD_COLUMNS,
        "radiographs": RADIOGRAPH_COLUMNS,
        "summary": SUMMARY_COLUMNS,
    }[what]
