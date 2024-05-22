"""Location markers, and proving the whole weld got shot.

A location marker is a lead number laid on the part before the shot. Its shadow
lands on the film, so the film can be tied to a place on the weld years later
without anybody remembering anything. Two rules make it work and both are the
kind that get skipped when it is cold.

The marker goes on the part, not on the film holder. A marker taped to the
cassette proves where the cassette was, which is not the question. If the
cassette slipped, and cassettes slip, a marker on the holder slips with it and
the radiograph lies about which piece of weld it shows.

Consecutive films have to overlap and the overlap has to be visible. A butt
joint between two films with no overlap leaves a strip of weld that is on
neither, and there is no way to tell afterwards how wide that strip was. So the
plan works in effective coverage, which is the film length minus the overlap,
and the film count comes out of that rather than out of the raw length.

Coverage is proved by markers, not by arithmetic. The plan says which markers
should appear on which film. The reader says which ones did. The difference is
the gap.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_CEILING, Decimal

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.length import Length

MINIMUM_OVERLAP = Length.from_mm(25)
"""An inch, which is what the written practice asks for and what fits."""


@dataclass(frozen=True, slots=True)
class Marker:
    """One lead number and where it sits along the weld."""

    label: str
    at: Length

    def __post_init__(self) -> None:
        if not self.label.strip():
            raise ValidationError("a marker needs a number")
        if self.at.um < 0:
            raise ValidationError("a marker sits somewhere on the weld", label=self.label)

    def __str__(self) -> str:
        return f"{self.label} at {self.at.format_mm(0)}"


@dataclass(frozen=True, slots=True)
class FilmSpan:
    """One film, where it starts and where it ends along the weld."""

    index: int
    starts_at: Length
    ends_at: Length

    def __post_init__(self) -> None:
        if self.ends_at <= self.starts_at:
            raise ValidationError("a film covers a positive length", index=self.index)

    @property
    def length(self) -> Length:
        return self.ends_at - self.starts_at

    def contains(self, at: Length) -> bool:
        return self.starts_at <= at <= self.ends_at

    def markers_within(self, markers: tuple[Marker, ...]) -> tuple[Marker, ...]:
        return tuple(m for m in markers if self.contains(m.at))


def effective_coverage(film_length: Length, overlap: Length = MINIMUM_OVERLAP) -> Length:
    """What one film adds to the total once the overlap is given back."""
    if film_length.um <= 0:
        raise ValidationError("a film has a length")
    if overlap.um < 0:
        raise ValidationError("an overlap cannot be negative")
    if overlap >= film_length:
        raise TechniqueError(
            "the overlap swallows the film",
            film_mm=str(film_length.mm),
            overlap_mm=str(overlap.mm),
        )
    return film_length - overlap


def films_required(
    weld_length: Length, film_length: Length, overlap: Length = MINIMUM_OVERLAP
) -> int:
    """How many films it takes, counting the overlap that is given back."""
    if weld_length.um <= 0:
        raise ValidationError("a weld has a length")
    if weld_length <= film_length:
        return 1
    step = effective_coverage(film_length, overlap)
    remaining = Decimal(weld_length.um - film_length.um) / Decimal(step.um)
    return 1 + int(remaining.to_integral_value(rounding=ROUND_CEILING))


def plan_films(
    weld_length: Length, film_length: Length, overlap: Length = MINIMUM_OVERLAP
) -> tuple[FilmSpan, ...]:
    """Where each film starts and ends, with the last one pulled back to the end."""
    count = films_required(weld_length, film_length, overlap)
    step = effective_coverage(film_length, overlap)
    spans: list[FilmSpan] = []
    for index in range(count):
        start = Length(step.um * index)
        end = start + film_length
        if end > weld_length:
            end = weld_length
            start = Length(max(0, end.um - film_length.um))
        spans.append(FilmSpan(index=index + 1, starts_at=start, ends_at=end))
    return tuple(spans)


def plan_markers(spans: tuple[FilmSpan, ...]) -> tuple[Marker, ...]:
    """A marker at the start of every film and one at the far end."""
    if not spans:
        raise ValidationError("there are no films to mark")
    markers = [Marker(label=str(index), at=span.starts_at) for index, span in enumerate(spans, 1)]
    markers.append(Marker(label=str(len(spans) + 1), at=spans[-1].ends_at))
    return tuple(markers)


def coverage_gaps(spans: tuple[FilmSpan, ...], weld_length: Length) -> tuple[str, ...]:
    """Anywhere along the weld that no film reaches."""
    if not spans:
        return ("no films are planned",)
    ordered = sorted(spans, key=lambda s: s.starts_at)
    gaps: list[str] = []
    if ordered[0].starts_at.um > 0:
        gaps.append(f"nothing covers the first {ordered[0].starts_at.format_mm(0)}")
    reach = ordered[0].ends_at
    for span in ordered[1:]:
        if span.starts_at > reach:
            gaps.append(f"a gap from {reach.format_mm(0)} to {span.starts_at.format_mm(0)}")
        reach = max(reach, span.ends_at)
    if reach < weld_length:
        gaps.append(f"nothing covers past {reach.format_mm(0)}")
    return tuple(gaps)


def missing_markers(
    expected: tuple[Marker, ...], seen_labels: frozenset[str]
) -> tuple[Marker, ...]:
    """Planned markers whose images did not turn up on any radiograph."""
    return tuple(m for m in expected if m.label not in seen_labels)
