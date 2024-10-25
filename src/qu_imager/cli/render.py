"""Printing tables and numbers so they line up in a terminal.

Nothing here uses colour and nothing uses box drawing characters. The output of
this tool gets pasted into emails, printed on a dot matrix in a site office,
and read over a radio, and every one of those turns a colour escape into
garbage. Plain columns, two spaces between them, and a rule made of dashes.

Numbers are right aligned and text is left aligned, which is the one formatting
rule that is worth the code because a column of right aligned exposure times
can be scanned for the long one and a column of left aligned ones cannot.

Widths come from the content rather than being fixed. A fixed width table with
a long site name in it wraps, and a wrapped table is worse than an ugly one.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

from qu_imager.errors import ValidationError


def widths(rows: Sequence[Sequence[str]]) -> tuple[int, ...]:
    if not rows:
        return ()
    columns = len(rows[0])
    for row in rows:
        if len(row) != columns:
            raise ValidationError(
                "every row has the same number of columns", expected=columns, got=len(row)
            )
    return tuple(max(len(row[index]) for row in rows) for index in range(columns))


def table(
    headings: Sequence[str],
    rows: Sequence[Sequence[str]],
    numeric: Iterable[int] = (),
) -> str:
    """A plain table. Columns named in numeric are right aligned."""
    if not headings:
        raise ValidationError("a table has headings")
    right = set(numeric)
    everything = [list(headings), *[list(r) for r in rows]]
    sizes = widths(everything)
    lines = [_line(headings, sizes, right), "  ".join("-" * size for size in sizes)]
    lines.extend(_line(row, sizes, right) for row in rows)
    return "\n".join(lines)


def _line(row: Sequence[str], sizes: Sequence[int], right: set[int]) -> str:
    cells = []
    for index, cell in enumerate(row):
        size = sizes[index]
        cells.append(cell.rjust(size) if index in right else cell.ljust(size))
    return "  ".join(cells).rstrip()


def pairs(items: Sequence[tuple[str, str]]) -> str:
    """A two column label and value block, which is what a sheet prints as."""
    if not items:
        return ""
    width = max(len(label) for label, _value in items)
    return "\n".join(f"{label.ljust(width)}  {value}" for label, value in items)


def bullets(items: Sequence[str], marker: str = "-") -> str:
    return "\n".join(f"{marker} {item}" for item in items)


def heading(text: str) -> str:
    return f"{text}\n{'=' * len(text)}"


def yes_no(value: bool) -> str:
    return "yes" if value else "no"
