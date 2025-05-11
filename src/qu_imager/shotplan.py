"""The pre job package: the technique sheet plus everything the site imposes.

A technique sheet says what would make a good radiograph. It says nothing about
whether the job can be done at all, and on a running plant that is the harder
question. The weld is in a pipe rack with forty metres of clear ground round it
and the restricted boundary comes out at a hundred and thirty. Something has to
give, and the options are a collimator, a barrier, a smaller source, or coming
back at three in the morning when the unit is down.

So this module takes the sheet and applies the site: how much room there is,
who is on the crew, what they are carrying, and how long the window is. What
comes back is a plan with a list of blockers, and a blocker is not a warning. A
sheet with warnings gets signed by a Level III who has read them. A plan with
blockers does not go out.

The order the blockers are collected in is the order a supervisor would find
them walking the job, and every one is reported rather than the first, because
a crew sent back twice for two different reasons has lost a night either way.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from qu_imager.errors import ValidationError
from qu_imager.personnel import Person, interpreters, supervision_satisfied
from qu_imager.quantities.dose import Dose
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length
from qu_imager.safety.dosimetry import Assignment
from qu_imager.safety.field import BoundaryKind, RadiationField, RopeOff, rope_off
from qu_imager.technique import TechniqueSheet


@dataclass(frozen=True, slots=True)
class SiteConstraints:
    """What the ground round the weld will and will not allow."""

    clear_radius: Length
    window: Duration | None = None
    occupied_beyond: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if self.clear_radius.um <= 0:
            raise ValidationError("a site has some clear ground or the job does not happen")


@dataclass(frozen=True, slots=True)
class CrewDose:
    """What the crew is expected to take on this job, and where they stand."""

    badge: str
    expected: Dose
    stand_off: Length

    def describe(self) -> str:
        return f"{self.badge} {self.expected.format_usv()} at {self.stand_off.format_m()}"


@dataclass(frozen=True, slots=True)
class ShotPlan:
    """Everything that has to be true before the source comes out."""

    sheet: TechniqueSheet
    radiation_field: RadiationField
    rope: RopeOff
    site: SiteConstraints
    blockers: tuple[str, ...]
    warnings: tuple[str, ...]
    expected_dose: tuple[CrewDose, ...] = ()

    @property
    def can_proceed(self) -> bool:
        return not self.blockers

    @property
    def needs_a_signature(self) -> bool:
        return bool(self.warnings)

    def describe(self) -> str:
        if self.blockers:
            return "; ".join(self.blockers)
        return f"{self.sheet.describe()}, {self.rope.describe()}"


def plan_shot(
    sheet: TechniqueSheet,
    radiation_field: RadiationField,
    site: SiteConstraints,
    crew: tuple[Person, ...],
    assignments: tuple[Assignment, ...],
    day: date,
) -> ShotPlan:
    """Apply the site to the sheet and collect everything in the way."""
    rope = rope_off(radiation_field)
    blockers: list[str] = []

    blockers.extend(_room_blockers(rope, site))
    blockers.extend(_crew_blockers(crew, assignments, day))
    blockers.extend(_window_blockers(sheet, site))

    return ShotPlan(
        sheet=sheet,
        radiation_field=radiation_field,
        rope=rope,
        site=site,
        blockers=tuple(blockers),
        warnings=sheet.warnings,
        expected_dose=tuple(
            expected_dose(radiation_field, sheet, assignment.badge, rope.down_beam)
            for assignment in assignments
        ),
    )


def expected_dose(
    radiation_field: RadiationField,
    sheet: TechniqueSheet,
    badge: str,
    stand_off: Length,
) -> CrewDose:
    """What somebody standing at the rope takes while the source is out.

    It is a small number and it should be. A crew that expects a measurable
    dose from standing at the boundary has the boundary in the wrong place,
    and this exists to make that visible before the shot rather than on the
    badge report three months later.
    """
    if stand_off.um <= 0:
        raise ValidationError("somebody has to be standing somewhere")
    rate = radiation_field.rate_at(stand_off, Decimal(0))
    return CrewDose(badge=badge, expected=rate.over(sheet.total_source_out), stand_off=stand_off)


def over_expected(plan: ShotPlan, ceiling: Dose) -> tuple[str, ...]:
    """Anybody whose expected dose on this job is over a stated ceiling."""
    return tuple(entry.badge for entry in plan.expected_dose if entry.expected > ceiling)


def _room_blockers(rope: RopeOff, site: SiteConstraints) -> list[str]:
    if rope.down_beam <= site.clear_radius:
        return []
    return [
        f"the {rope.kind} boundary is {rope.down_beam.format_m()} and the site has "
        f"{site.clear_radius.format_m()} of clear ground"
    ]


def _crew_blockers(
    crew: tuple[Person, ...], assignments: tuple[Assignment, ...], day: date
) -> list[str]:
    out: list[str] = []
    if not crew:
        out.append("there is no crew")
        return out
    if not supervision_satisfied(crew, day):
        out.append("no licensed radiographer is on the crew")
    if not interpreters(crew, day):
        out.append("nobody on the crew may interpret the film")
    for assignment in assignments:
        for missing in assignment.missing_on(day):
            out.append(f"{assignment.badge}: {missing}")
    return out


def _window_blockers(sheet: TechniqueSheet, site: SiteConstraints) -> list[str]:
    if site.window is None:
        return []
    if sheet.total_source_out > site.window:
        return [
            f"the joint needs {sheet.total_source_out.as_timer()} of source out and the "
            f"window is {site.window.as_timer()}"
        ]
    return []


def collimator_would_help(radiation_field: RadiationField, site: SiteConstraints) -> bool:
    """Whether cutting the side field alone brings the job inside the ground."""
    off_beam = radiation_field.boundary(BoundaryKind.RESTRICTED, Decimal(180))
    return off_beam > site.clear_radius


def shortfall(rope: RopeOff, site: SiteConstraints) -> Length:
    """How much more clear ground the job needs. Zero when it fits."""
    if rope.down_beam <= site.clear_radius:
        return Length.ZERO
    return rope.down_beam - site.clear_radius
