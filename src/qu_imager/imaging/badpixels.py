"""Bad pixel maps, and why the count is the least interesting number in one.

Every panel ships with dead pixels and nobody minds. What matters is where they
are and whether they touch. A thousand isolated dead pixels scattered across
four million are invisible after interpolation, because each one is surrounded
by eight good neighbours that can fill it in. Nine dead pixels in a square are
not invisible at all: the middle one has no good neighbour, nothing can fill
it, and a flaw that lands there is gone.

So the map is analysed as clusters rather than counted. A cluster is a set of
bad pixels connected side to side or corner to corner, which is the right
connectivity because a diagonal pair still robs the pixel between them of two
of its neighbours.

Lines are treated separately from blobs. A dead readout line is thin, so it is
a small cluster by area, and it is far worse than a compact cluster of the same
size because it cuts across the image rather than sitting in it.

The limits are a policy, not a law. Every client specification sets its own and
this module takes them as an argument rather than baking them in.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError

Pixel = tuple[int, int]

_NEIGHBOURS: tuple[Pixel, ...] = (
    (-1, -1),
    (-1, 0),
    (-1, 1),
    (0, -1),
    (0, 1),
    (1, -1),
    (1, 0),
    (1, 1),
)


@dataclass(frozen=True, slots=True)
class Cluster:
    """A connected group of bad pixels."""

    pixels: frozenset[Pixel]

    @property
    def size(self) -> int:
        return len(self.pixels)

    @property
    def bounds(self) -> tuple[int, int, int, int]:
        columns = [c for c, _r in self.pixels]
        rows = [r for _c, r in self.pixels]
        return (min(columns), min(rows), max(columns), max(rows))

    @property
    def width(self) -> int:
        left, _top, right, _bottom = self.bounds
        return right - left + 1

    @property
    def height(self) -> int:
        _left, top, _right, bottom = self.bounds
        return bottom - top + 1

    @property
    def is_line(self) -> bool:
        """One pixel wide in either direction and longer than it is thick."""
        return (self.width == 1 or self.height == 1) and self.size > 1

    @property
    def length(self) -> int:
        return max(self.width, self.height)


@dataclass(frozen=True, slots=True)
class BadPixelPolicy:
    """What a client specification allows."""

    max_fraction: Decimal
    max_cluster: int
    max_line: int

    def __post_init__(self) -> None:
        if not 0 <= self.max_fraction <= 1:
            raise ValidationError("a bad pixel fraction sits in [0, 1]")
        if self.max_cluster < 1 or self.max_line < 1:
            raise ValidationError("cluster and line limits are at least one pixel")


DEFAULT_POLICY = BadPixelPolicy(max_fraction=Decimal("0.02"), max_cluster=5, max_line=10)


@dataclass(frozen=True, slots=True)
class BadPixelMap:
    """The bad pixels on one panel."""

    columns: int
    rows: int
    pixels: frozenset[Pixel]

    def __post_init__(self) -> None:
        if self.columns <= 0 or self.rows <= 0:
            raise ValidationError("a map needs a panel size")
        for column, row in self.pixels:
            if not (0 <= column < self.columns and 0 <= row < self.rows):
                raise ValidationError("a bad pixel is off the panel", column=column, row=row)

    @property
    def total(self) -> int:
        return len(self.pixels)

    @property
    def fraction(self) -> Decimal:
        return Decimal(self.total) / Decimal(self.columns * self.rows)

    def clusters(self) -> tuple[Cluster, ...]:
        """Connected components, eight way, largest first."""
        remaining = set(self.pixels)
        found: list[Cluster] = []
        while remaining:
            seed = remaining.pop()
            group = {seed}
            frontier = [seed]
            while frontier:
                column, row = frontier.pop()
                for dc, dr in _NEIGHBOURS:
                    neighbour = (column + dc, row + dr)
                    if neighbour in remaining:
                        remaining.remove(neighbour)
                        group.add(neighbour)
                        frontier.append(neighbour)
            found.append(Cluster(frozenset(group)))
        return tuple(sorted(found, key=lambda c: (-c.size, sorted(c.pixels)[0])))

    def largest_cluster(self) -> Cluster | None:
        found = self.clusters()
        return found[0] if found else None

    def lines(self) -> tuple[Cluster, ...]:
        return tuple(c for c in self.clusters() if c.is_line)

    def violations(self, policy: BadPixelPolicy = DEFAULT_POLICY) -> tuple[str, ...]:
        out: list[str] = []
        if self.fraction > policy.max_fraction:
            out.append(
                f"{self.total} bad pixels is {self.fraction:.4f} of the panel, "
                f"over {policy.max_fraction}"
            )
        for cluster in self.clusters():
            if cluster.is_line:
                if cluster.length > policy.max_line:
                    out.append(
                        f"a bad line of {cluster.length} pixels at {sorted(cluster.pixels)[0]}"
                    )
            elif cluster.size > policy.max_cluster:
                out.append(f"a cluster of {cluster.size} pixels at {sorted(cluster.pixels)[0]}")
        return tuple(out)

    def passes(self, policy: BadPixelPolicy = DEFAULT_POLICY) -> bool:
        return not self.violations(policy)
