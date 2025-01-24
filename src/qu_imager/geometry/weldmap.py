"""The weld map, which is the sketch that proves the whole circumference was shot.

A girth weld is a circle and the films are arcs of it. Complete coverage means
the arcs, taken together, close the circle with overlap at every join. That is
easy to say and awkward to check, because the arcs wrap: an arc from three
hundred and thirty degrees to thirty degrees crosses twelve o'clock and is one
arc, not two.

So everything here works in degrees clockwise from twelve, arcs carry their own
wrap, and the gap check walks the circle once from a fixed start rather than
sorting and comparing neighbours. Sorting arcs that wrap puts the one crossing
twelve o'clock in the wrong place and produces a gap report that says the weld
is uncovered at the top when it is the only part that was shot twice.

The map is also what a repair crew reads. An indication at four o'clock on film
two has to be findable on the pipe, and the only thing that makes that work is
the film knowing which arc it covers.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from qu_imager.errors import ValidationError

FULL_CIRCLE = Decimal(360)
DEGREE = chr(176)
"""Written as a code point so the source stays plain ascii on every machine."""


def wrap(degrees: Decimal) -> Decimal:
    """Fold any bearing into zero up to but not including three sixty.

    Decimal takes the sign of the dividend from the left operand, so a bearing
    of minus ten comes back as minus ten rather than as three hundred and
    fifty. That is the opposite of what integer modulo does and it is the bug
    that made an arc crossing twelve read as two arcs.
    """
    turned = degrees % FULL_CIRCLE
    return turned + FULL_CIRCLE if turned < 0 else turned


@dataclass(frozen=True, slots=True)
class Arc:
    """A stretch of the circumference, clockwise from start to end."""

    label: str
    starts_at: Decimal
    span: Decimal

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("an arc has a label")
        if self.span <= 0:
            raise ValidationError("an arc has a positive span", label=self.label)
        if self.span > FULL_CIRCLE:
            raise ValidationError("an arc cannot go round twice", label=self.label)

    @property
    def ends_at(self) -> Decimal:
        return wrap(self.starts_at + self.span)

    @property
    def wraps(self) -> bool:
        return self.starts_at + self.span > FULL_CIRCLE

    def contains(self, bearing: Decimal) -> bool:
        offset = wrap(bearing - self.starts_at)
        return offset < self.span

    def overlap_with(self, other: Arc) -> Decimal:
        """How much of the circle both arcs cover, walked rather than sorted."""
        step = Decimal("0.5")
        covered = Decimal(0)
        bearing = Decimal(0)
        while bearing < FULL_CIRCLE:
            if self.contains(bearing) and other.contains(bearing):
                covered += step
            bearing += step
        return covered

    def describe(self) -> str:
        return f"{self.label} {self.starts_at}\u00b0 for {self.span}\u00b0"


@dataclass(frozen=True, slots=True)
class Gap:
    """A stretch nothing covers."""

    starts_at: Decimal
    span: Decimal

    def describe(self) -> str:
        return f"{self.starts_at}\u00b0 to {wrap(self.starts_at + self.span)}\u00b0"


@dataclass(frozen=True, slots=True)
class Map:
    """Every arc on one girth weld."""

    arcs: tuple[Arc, ...]

    def covers(self, bearing: Decimal) -> tuple[str, ...]:
        return tuple(arc.label for arc in self.arcs if arc.contains(bearing))

    def gaps(self, step: Decimal = Decimal("0.5")) -> tuple[Gap, ...]:
        """Walk the circle once from twelve and collect what nothing reaches."""
        if step <= 0 or step > 10:
            raise ValidationError("a walk step is small and positive", step=str(step))
        found: list[Gap] = []
        bearing = Decimal(0)
        run_start: Decimal | None = None
        while bearing < FULL_CIRCLE:
            uncovered = not self.covers(bearing)
            if uncovered and run_start is None:
                run_start = bearing
            elif not uncovered and run_start is not None:
                found.append(Gap(run_start, bearing - run_start))
                run_start = None
            bearing += step
        if run_start is not None:
            found.append(Gap(run_start, FULL_CIRCLE - run_start))
        return _stitch(tuple(found))

    @property
    def is_complete(self) -> bool:
        return not self.gaps()

    def single_covered(self, step: Decimal = Decimal("0.5")) -> tuple[Gap, ...]:
        """Stretches only one film reaches, which have no overlap to prove them."""
        found: list[Gap] = []
        bearing = Decimal(0)
        run_start: Decimal | None = None
        while bearing < FULL_CIRCLE:
            thin = len(self.covers(bearing)) == 1
            if thin and run_start is None:
                run_start = bearing
            elif not thin and run_start is not None:
                found.append(Gap(run_start, bearing - run_start))
                run_start = None
            bearing += step
        if run_start is not None:
            found.append(Gap(run_start, FULL_CIRCLE - run_start))
        return _stitch(tuple(found))


def _stitch(found: tuple[Gap, ...]) -> tuple[Gap, ...]:
    """Join a run that ends at three sixty to one that starts at zero.

    The walk starts at twelve, so a stretch straddling twelve comes out as two
    runs, one at each end of the walk. They are one stretch on the pipe and
    reporting them as two is how a report ends up claiming a weld has a gap at
    the top and another at the top.
    """
    if len(found) < 2:
        return found
    first, last = found[0], found[-1]
    if first.starts_at != 0 or wrap(last.starts_at + last.span) != 0:
        return found
    joined = Gap(last.starts_at, last.span + first.span)
    return (*found[1:-1], joined)


def even_arcs(count: int, overlap_degrees: Decimal = Decimal(10)) -> Map:
    """The usual layout: equal films with a stated overlap at every join."""
    if count < 1:
        raise ValidationError("a weld takes at least one film", count=count)
    if overlap_degrees < 0:
        raise ValidationError("an overlap is not negative")
    step = FULL_CIRCLE / count
    span = step + overlap_degrees
    if span > FULL_CIRCLE:
        span = FULL_CIRCLE
    return Map(tuple(Arc(str(i + 1), wrap(step * i), span) for i in range(count)))
