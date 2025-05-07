"""The technique sheet: everything decided before the source comes out.

This is the spine. A weld, a source and an imaging system go in, and what comes
out is the piece of paper the crew works off: which shot, how many, how far
back, which indicator, how long, and everything that is wrong with the answer.

The order matters and it is not arbitrary. Geometry first, because the shot
type decides the penetrated thickness and the penetrated thickness decides
everything downstream. Then the distance, which is bounded from below by
unsharpness and from above by exposure time. Then the indicator, which is read
off the penetrated thickness. Then coverage, which is read off the distance.
Then the exposure, which needs all of it plus the source decayed to the day.

Nothing here throws on a plan that is merely bad. A sheet with warnings on it
is a real thing that a Level III looks at and either signs or sends back. What
does throw is a plan that cannot exist: no shot type available, a thickness off
the end of a chart, a source with nothing left in it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

from qu_imager.errors import TechniqueError
from qu_imager.exposure.calculation import ExposureResult, plan_exposure
from qu_imager.exposure.catalogue import chart_for
from qu_imager.exposure.corrections import (
    Correction,
    CorrectionChain,
    collimator_correction,
    distance_correction,
    film_correction,
)
from qu_imager.geometry.beam import BeamGeometry, minimum_source_to_film, unsharpness_limit
from qu_imager.geometry.coverage import CoveragePlan, plan_coverage
from qu_imager.geometry.joint import Joint
from qu_imager.geometry.shot import ShotType, choose_shot, exposures_required, penetrated_thickness
from qu_imager.imaging.film import REFERENCE_FILM, FilmSystem
from qu_imager.imaging.screens import screens_for, wrong_screens
from qu_imager.imaging.selection import HoleSelection, IndicatorSide, hole_selection
from qu_imager.imaging.system import ImagingSystem
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length
from qu_imager.sources.device import Collimator, collimator
from qu_imager.sources.isotope import Isotope
from qu_imager.sources.sealed import SealedSource

GUIDE_CLEARANCE = Length.from_mm(5)


def coverage_for(joint: Joint, shot: ShotType, geometry: BeamGeometry) -> CoveragePlan:
    """A panoramic shot takes the whole circumference at once and nothing else does."""
    if shot is ShotType.PANORAMIC:
        return CoveragePlan(
            exposures=1,
            usable_half_arc_degrees=Decimal(180),
            separation_degrees=Decimal(360),
            overlap_degrees=Decimal(0),
        )
    if not joint.form.is_circumferential:
        return CoveragePlan(
            exposures=1,
            usable_half_arc_degrees=Decimal(180),
            separation_degrees=Decimal(360),
            overlap_degrees=Decimal(0),
        )
    standoff = geometry.source_to_object
    return plan_coverage(joint, standoff, exposures_required(joint, shot))


def geometry_for(joint: Joint, shot: ShotType, focal: Length) -> BeamGeometry:
    """Where the source ends up, which the shot type decides rather than the planner.

    An inside shot has no free distance in it. A panoramic source sits on the
    axis, so the distance is the pipe radius and nothing can be done about it.
    A single wall single image source lies against the far bore, so the beam
    crosses the whole inside diameter and then one wall.

    An outside shot is the other case entirely. The standoff is free, so it is
    set to the closest the unsharpness rule allows, and the exposure time comes
    out of that rather than the other way round.
    """
    if shot is ShotType.PANORAMIC:
        return BeamGeometry(
            source_to_object=joint.inside_diameter / 2,
            object_to_film=joint.wall,
            focal_size=focal,
        )
    if shot is ShotType.SWSI:
        return BeamGeometry(
            source_to_object=joint.inside_diameter,
            object_to_film=joint.wall,
            focal_size=focal,
        )
    gap = joint.wall if shot is ShotType.CONTACT else joint.od
    sfd = minimum_source_to_film(focal, gap, joint.wall)
    return BeamGeometry.from_sfd(sfd, gap, focal)


@dataclass(frozen=True, slots=True)
class TechniqueRequest:
    """What the office knows before it starts."""

    joint: Joint
    source: SealedSource
    system: ImagingSystem
    film: FilmSystem
    shot_at: datetime
    shot: ShotType | None = None
    screens_fitted: bool = True
    collimator_key: str | None = None
    indicator_side: IndicatorSide = IndicatorSide.SOURCE
    window: Duration | None = None
    density: Decimal = Decimal("2.0")


@dataclass(frozen=True, slots=True)
class TechniqueSheet:
    """The plan, with everything that is wrong with it attached."""

    joint: Joint
    shot: ShotType
    penetrated: Length
    geometry: BeamGeometry
    source_to_film: Length
    coverage: CoveragePlan
    indicator: HoleSelection
    exposure: ExposureResult
    total_source_out: Duration
    warnings: tuple[str, ...]

    @property
    def is_clean(self) -> bool:
        return not self.warnings

    @property
    def exposures(self) -> int:
        return self.coverage.exposures

    def describe(self) -> str:
        return (
            f"{self.joint.describe()}, {self.shot}, "
            f"{self.exposures} at {self.source_to_film.format_mm(0)}, "
            f"{self.indicator.describe()}, {self.exposure.timer} each"
        )


def plan_technique(request: TechniqueRequest) -> TechniqueSheet:
    """Work the sheet through in the order the numbers depend on each other."""
    joint = request.joint
    isotope = request.source.isotope
    focal = request.source.certificate.focal_size

    shot = request.shot or choose_shot(joint, focal, GUIDE_CLEARANCE)
    penetrated = penetrated_thickness(joint, shot)

    geometry = geometry_for(joint, shot, focal)
    sfd = geometry.source_to_film

    indicator = hole_selection(penetrated, request.indicator_side)

    coverage = coverage_for(joint, shot, geometry)

    chart = chart_for(isotope.key, joint.material.key, REFERENCE_FILM.key, request.density)
    chain = (
        CorrectionChain()
        .with_correction(distance_correction(chart.reference_sfd, sfd))
        .with_correction(film_correction(REFERENCE_FILM, request.film))
        .with_correction(_screen_correction(request, isotope))
    )
    if request.collimator_key is not None:
        cup = collimator(request.collimator_key)
        chain = chain.with_correction(collimator_correction(_beam_fraction(cup)))
    activity = request.source.activity_at(request.shot_at)
    exposure = plan_exposure(chart, penetrated, chain, activity, request.window)

    total = Duration(exposure.exposure.ms * coverage.exposures)
    warnings = _warnings(request, geometry, penetrated, exposure, total)
    warnings = (*warnings, *screen_faults(request, isotope))

    return TechniqueSheet(
        joint=joint,
        shot=shot,
        penetrated=penetrated,
        geometry=geometry,
        source_to_film=sfd,
        coverage=coverage,
        indicator=indicator,
        exposure=exposure,
        total_source_out=total,
        warnings=warnings,
    )


def _warnings(
    request: TechniqueRequest,
    geometry: BeamGeometry,
    penetrated: Length,
    exposure: ExposureResult,
    total: Duration,
) -> tuple[str, ...]:
    out: list[str] = list(exposure.warnings)
    reasons = request.source.reasons_not_to_use(request.shot_at)
    out.extend(f"source is not fit: {reason}" for reason in reasons)
    if not geometry.meets(request.joint.wall):
        out.append(
            f"geometric unsharpness {geometry.geometric_unsharpness.format_mm(3)} is over "
            f"{unsharpness_limit(request.joint.wall).format_mm(3)}"
        )
    if not request.source.isotope.covers(penetrated):
        out.append(
            f"{request.source.isotope} is outside its practical range at {penetrated.format_mm()}"
        )
    if request.window is not None and total > request.window:
        out.append(
            f"the joint needs {total.as_timer()} of source out and the window is "
            f"{request.window.as_timer()}"
        )
    return tuple(out)


def sheet_or_refuse(request: TechniqueRequest) -> TechniqueSheet:
    """Plan, and refuse outright if the sheet cannot be worked at all."""
    sheet = plan_technique(request)
    if not sheet.exposure.corrected_reading:
        raise TechniqueError("the exposure could not be worked out", joint=request.joint.describe())
    return sheet


def _screen_correction(request: TechniqueRequest, source: Isotope) -> Correction:
    """Lead screens shorten the shot, and the pair depends on the beam."""
    if not request.screens_fitted:
        return Correction("screens", Decimal(1), "no screens")
    quality = source.equivalence_quality
    if quality is None:
        return Correction("screens", Decimal(1), "no screen pair specified for this isotope")
    pair = screens_for(quality)
    return Correction("screens", pair.time_factor, pair.describe())


def _beam_fraction(cup: Collimator) -> Decimal:
    """A collimator clips the beam edge, so a little exposure is lost.

    The transmission on the cup is what leaks through the body of it, which is
    not the same number. What is lost down the beam is the edge of the cone,
    and the shop measures it at a few percent per cup rather than deriving it.
    """
    lost = Decimal(cup.aperture_degrees) / Decimal(3600)
    return Decimal(1) - lost


def screen_faults(request: TechniqueRequest, source: Isotope) -> tuple[str, ...]:
    """Whether the pair on the cassette belongs on this shot."""
    if not request.screens_fitted:
        return ()
    quality = source.equivalence_quality
    if quality is None:
        return ()
    note = wrong_screens(screens_for(quality), quality)
    return (note,) if note is not None else ()
