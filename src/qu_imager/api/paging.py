"""Paging, done on a cursor rather than on an offset.

An offset page is wrong the moment anything is inserted. A client walking a
job's radiographs at fifty a page, while a night crew is recording more of
them, sees some twice and misses others, and the ones it misses are the new
ones, which are the ones it wanted.

A cursor page is anchored to the last row the client saw. Rows inserted behind
the cursor do not shift it, so nothing is skipped, and the client can stop and
resume days later from the same cursor.

The cursor is the sort key rather than an opaque token. That is a deliberate
choice: an opaque token has to be decoded, which means a format, which means a
version, and this is a small enough system that a radiograph number as a cursor
is honest and debuggable. The cost is that the sort key has to be unique, so
every paged listing sorts on something with a unique constraint behind it.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, TypeVar

from qu_imager.errors import ValidationError

T = TypeVar("T")

DEFAULT_PAGE = 50
MAX_PAGE = 500


@dataclass(frozen=True, slots=True)
class Page(Generic[T]):
    """One page, and where the next one starts."""

    items: tuple[T, ...]
    next_cursor: str | None
    size: int

    @property
    def is_last(self) -> bool:
        return self.next_cursor is None

    @property
    def count(self) -> int:
        return len(self.items)


def clamp(size: int | None) -> int:
    """A page size the server is prepared to serve."""
    if size is None:
        return DEFAULT_PAGE
    if size < 1:
        raise ValidationError("a page holds at least one row", size=size)
    return min(size, MAX_PAGE)


def paginate(rows: list[T], key: object, size: int | None = None) -> Page[T]:
    """Cut a sorted list into one page, with the cursor for the next.

    One extra row is fetched by the caller and handed in, which is how the
    server knows whether there is a next page without counting the whole table.
    That is why this takes a list one longer than the page rather than doing
    the query itself.
    """
    wanted = clamp(size)
    if not callable(key):
        raise ValidationError("a cursor needs a key function")
    if len(rows) <= wanted:
        return Page(items=tuple(rows), next_cursor=None, size=wanted)
    kept = rows[:wanted]
    return Page(items=tuple(kept), next_cursor=str(key(kept[-1])), size=wanted)


def after(cursor: str | None) -> str | None:
    """Normalise a cursor from a query string, treating blank as absent."""
    if cursor is None:
        return None
    cleaned = cursor.strip()
    return cleaned or None


def fetch_size(size: int | None) -> int:
    """How many rows to ask the database for, which is one more than the page."""
    return clamp(size) + 1
