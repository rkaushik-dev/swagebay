"""Incidents and calibrations over http.

The incident endpoint returns the deadline it computed rather than taking one.
A client that could supply the deadline could supply the wrong one, and the
whole reason the occurrence class exists is that the deadline falls out of what
happened rather than out of anybody's judgement.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    CalibrationOut,
    IncidentIn,
    IncidentOut,
    InstrumentCalibrationIn,
)
from qu_imager.calibration import Certificate, Instrument
from qu_imager.errors import ValidationError
from qu_imager.persistence.models.registers import CalibrationRow, IncidentRow
from qu_imager.safety.incidents import Incident, Occurrence
from qu_imager.services.registers import (
    notify_incident,
    record_calibration,
    record_incident,
)

router = APIRouter(prefix="/registers", tags=["registers"])


@router.post("/incidents", status_code=status.HTTP_201_CREATED)
def create_incident(body: IncidentIn, work: Work) -> IncidentOut:
    incident = Incident(
        reference=body.reference,
        occurrence=_occurrence(body.occurrence),
        discovered_at=body.discovered_at,
        site=body.site,
        reported_by=body.reported_by,
        account=body.account,
    )
    record_incident(work, incident)
    return _incident_out(work.incidents.get(body.reference), datetime.now())


@router.post("/incidents/{reference}/notify")
def notify(reference: str, work: Work, at: datetime) -> IncidentOut:
    notify_incident(work, reference, at)
    return _incident_out(work.incidents.get(reference), at)


@router.get("/incidents")
def list_incidents(work: Work) -> list[IncidentOut]:
    now = datetime.now()
    return [_incident_out(row, now) for row in work.incidents.unreported()]


@router.get("/incidents/{reference}")
def read_incident(reference: str, work: Work) -> IncidentOut:
    return _incident_out(work.incidents.get(reference), datetime.now())


@router.post("/calibrations", status_code=status.HTTP_201_CREATED)
def create_calibration(body: InstrumentCalibrationIn, work: Work) -> CalibrationOut:
    record_calibration(
        work,
        body.serial,
        _instrument(body.instrument),
        Certificate(
            calibrated_on=body.calibrated_on,
            due_on=body.due_on,
            laboratory=body.laboratory,
            reference=body.certificate or body.serial,
        ),
    )
    latest = work.calibrations.latest_for(body.serial)
    assert latest is not None
    return _calibration_out(latest)


@router.get("/calibrations/expired")
def list_expired(work: Work, on: date | None = None) -> list[str]:
    return list(work.calibrations.expired_on(on or date.today()))


@router.get("/calibrations/{serial}")
def read_calibration(serial: str, work: Work) -> CalibrationOut | None:
    latest = work.calibrations.latest_for(serial)
    return _calibration_out(latest) if latest is not None else None


def _occurrence(value: str) -> Occurrence:
    wanted = value.strip()
    for member in Occurrence:
        if member.name.lower() == wanted.lower() or member.value == wanted:
            return member
    raise ValidationError(
        "no occurrence by that name",
        given=value,
        known=", ".join(member.name for member in Occurrence),
    )


def _instrument(value: str) -> Instrument:
    wanted = value.strip()
    for member in Instrument:
        if member.name.lower() == wanted.lower() or member.value == wanted:
            return member
    raise ValidationError(
        "no instrument by that name",
        given=value,
        known=", ".join(member.name for member in Instrument),
    )


def _incident_out(row: IncidentRow, at: datetime) -> IncidentOut:
    occurrence = _occurrence(row.occurrence)
    due = row.discovered_at + occurrence.deadline
    late = row.notified_at > due if row.notified_at is not None else at > due
    return IncidentOut(
        reference=row.reference,
        occurrence=row.occurrence,
        discovered_at=row.discovered_at,
        due_at=due,
        site=row.site,
        notified_at=row.notified_at,
        late=late,
    )


def _calibration_out(row: CalibrationRow) -> CalibrationOut:
    return CalibrationOut(
        serial=row.serial,
        instrument=row.instrument_kind,
        calibrated_on=row.calibrated_on,
        due_on=row.due_on,
        laboratory=row.laboratory,
    )
