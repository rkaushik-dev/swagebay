"""Radiographs over http: recording a shot and recording a reading.

Building the evidence object is the bulk of this file and it is not glamorous.
It is also the whole point: the caller sends what was measured, in the units
they measured it in, and this assembles it into the shape the rules want. A
caller that had to build the evidence itself would be a caller that has to know
which gate runs on which medium, and then there would be two copies of that
rule.

The one decision made here is which reader the badge refers to. The api does
not carry a personnel directory, so the caller supplies the reader's
credentials alongside the badge and the service checks them. That is the honest
shape until there is a directory, and it is written down so nobody mistakes it
for authentication.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    EntryOut,
    HistoryOut,
    ReadingIn,
    RereadIn,
    RereadOut,
    ShotIn,
    ShotOut,
)
from qu_imager.audit import Action
from qu_imager.errors import ValidationError
from qu_imager.evaluation.indication import Indication
from qu_imager.identity import ClockPosition, JobNumber, WeldId
from qu_imager.imaging.admissibility import Evidence, IndicatorEvidence
from qu_imager.imaging.computed import plate
from qu_imager.imaging.density import Calibration, DensityVerdict, Reading, grade
from qu_imager.imaging.film import film as film_system
from qu_imager.imaging.indicators import HoleIndicator, HoleSize, WireIndicator
from qu_imager.imaging.noise import NoiseMeasurement
from qu_imager.imaging.system import ImagingSystem
from qu_imager.naming import kind_named, technique_class_named
from qu_imager.personnel import Level, LicenceRole, Person
from qu_imager.quantities.length import Length
from qu_imager.services.reading import (
    ReadingRecord,
    RereadRequest,
    readers_of,
    record_reading,
    record_reread,
)
from qu_imager.services.shooting import ShotRecord, record_shot

router = APIRouter(prefix="/jobs/{number}/welds/{weld}", tags=["radiographs"])


@router.post("/shots", status_code=status.HTTP_201_CREATED)
def record(number: str, weld: str, body: ShotIn, work: Work) -> ShotOut:
    result = record_shot(
        work,
        JobNumber.parse(number),
        ShotRecord(
            weld=WeldId.parse(weld),
            exposure_index=body.exposure_index,
            exposed_at=body.exposed_at,
            technique_reference=body.technique_reference,
            evidence=_evidence(body),
        ),
    )
    return ShotOut(
        number=result.number,
        admissible=result.admissibility.admissible,
        reasons=result.admissibility.reasons,
        gates_run=result.admissibility.gates_run,
    )


@router.post("/radiographs/{radiograph}/reading")
def read(number: str, weld: str, radiograph: str, body: ReadingIn, work: Work) -> dict[str, str]:
    record_reading(
        work,
        ReadingRecord(
            radiograph_number=radiograph,
            reader=_reader(body.reader_badge, body.read_at),
            read_at=body.read_at,
            indications=tuple(_indication(item) for item in body.indications),
        ),
    )
    return {"radiograph": radiograph, "read_by": body.reader_badge}


def _system(body: ShotIn) -> ImagingSystem:
    if body.medium == "film":
        return ImagingSystem.from_film(film_system(body.film))
    return ImagingSystem.from_plate(plate(body.film))


def _evidence(body: ShotIn) -> Evidence:
    return Evidence(
        system=_system(body),
        penetrated=body.penetrated.to_length(),
        technique_class=technique_class_named(body.technique_class),
        indicator=_indicator(body),
        density=_density(body),
        noise=_noise(body),
    )


def _indicator(body: ShotIn) -> IndicatorEvidence:
    if body.indicator.hole_identity is not None:
        return IndicatorEvidence(
            required_hole=HoleIndicator(body.indicator.hole_identity),
            required_hole_size=HoleSize.TWO_T,
            hole_visible=body.indicator.hole_visible,
        )
    if body.indicator.wire_number is None:
        raise ValidationError("a shot names a hole indicator or a wire one")
    return IndicatorEvidence(
        required_wire=WireIndicator(body.indicator.wire_number),
        finest_wire_visible=body.indicator.finest_wire_visible,
    )


def _density(body: ShotIn) -> DensityVerdict | None:
    if not body.densities:
        return None
    readings = tuple(
        Reading(item.label, item.density, item.through_indicator) for item in body.densities
    )
    check = body.calibration
    if check is None:
        return grade(readings, gamma=body.gamma)
    return grade(
        readings,
        gamma=body.gamma,
        calibration=Calibration(
            checked_at=check.checked_at,
            step_nominal=check.step_nominal,
            step_read=check.step_read,
            instrument=check.instrument,
        ),
        read_at=body.exposed_at,
    )


def _noise(body: ShotIn) -> NoiseMeasurement | None:
    if body.mean_grey is None or body.noise is None or body.resolution is None:
        return None
    return NoiseMeasurement(
        label="parent metal",
        mean_grey=body.mean_grey,
        standard_deviation=body.noise,
        resolution=body.resolution.to_length(),
    )


def _indication(item) -> Indication:  # noqa: ANN001
    return Indication(
        kind=kind_named(item.kind),
        starts_at=item.starts_at.to_length(),
        length=item.length.to_length(),
        width=item.width.to_length() if item.width is not None else Length.ZERO,
        depth_fraction=item.depth_fraction,
        clock=ClockPosition.parse(item.clock) if item.clock is not None else None,
    )


def _reader(badge: str, read_at: datetime) -> Person:
    """The reader as the caller asserts them. Not authentication, and it says so."""
    day: date = read_at.date()
    return Person(
        badge=badge,
        name=badge,
        level=Level.LEVEL_II,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=day,
        near_vision_on=day,
        colour_perception_on=day,
    )


@router.post("/radiographs/{radiograph}/reread")
def reread(number: str, weld: str, radiograph: str, body: RereadIn, work: Work) -> RereadOut:
    """A second reader's call on a film somebody has already read.

    This is a post to its own path rather than a put on the reading, because it
    is not an edit of the reading. The first reading stays exactly where it
    was. Putting it behind put would tell every caller the opposite.
    """
    record_reread(
        work,
        RereadRequest(
            radiograph_number=radiograph,
            reader=_reader(body.reader_badge, body.read_at),
            read_at=body.read_at,
            reason=body.reason,
            indications=tuple(_indication(item) for item in body.indications),
        ),
    )
    history = work.audit.for_record("radiograph", radiograph)
    return RereadOut(
        radiograph=radiograph,
        readers=readers_of(work, radiograph),
        changed=any(e.action is Action.CHANGED for e in history),
    )


@router.get("/radiographs/{radiograph}/history")
def history(number: str, weld: str, radiograph: str, work: Work) -> HistoryOut:
    """Everything the trail holds about one film, oldest first."""
    entries = work.audit.for_record("radiograph", radiograph)
    if not entries:
        work.welds.radiograph(radiograph)
    return HistoryOut(
        record="radiograph",
        identity=radiograph,
        entries=tuple(
            EntryOut(
                at=entry.at,
                actor=entry.actor,
                action=str(entry.action),
                field_name=entry.field_name,
                was=entry.was,
                became=entry.became,
                reason=entry.reason,
            )
            for entry in entries
        ),
        after_signing=len(work.audit.changed_after_signing("radiograph", radiograph)),
    )
