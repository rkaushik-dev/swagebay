"""Devices over http: recording an inspection and asking the gate.

Inspections go in as one post with the defects on the body, for the same reason
the command line takes them on one line. A form that needs three requests is a
form that gets filled in later.

The clearance endpoint is a get rather than a post because it changes nothing,
and it answers with the reasons rather than with a status code alone, because a
dispatcher who is told no needs to know which of four things to go and fix.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import ClearanceOut, InspectionIn, InspectionOut
from qu_imager.errors import ValidationError
from qu_imager.safety.maintenance import Defect, Item, pre_use, quarterly
from qu_imager.services.dispatch import clearance

router = APIRouter(prefix="/devices", tags=["devices"])


def _item(key: str) -> Item:
    try:
        return Item[key.strip().upper().replace(" ", "_").replace("-", "_")]
    except KeyError:
        known = ", ".join(sorted(i.name.lower() for i in Item))
        raise ValidationError("nothing here inspects that", item=key, known=known) from None


@router.post("/{serial}/inspections", status_code=status.HTTP_201_CREATED)
def record_inspection(serial: str, body: InspectionIn, work: Work) -> InspectionOut:
    defects = tuple(Defect(_item(d.item), d.account) for d in body.defects)
    build = quarterly if body.is_quarterly else pre_use
    inspection = build(serial, body.performed_on, body.performed_by, defects, body.notes)
    work.maintenance.record(inspection)
    return InspectionOut(
        device_serial=serial,
        performed_on=inspection.performed_on,
        is_quarterly=inspection.is_quarterly,
        passed=inspection.passed,
        blocking=tuple(d.describe() for d in inspection.blocking_defects),
        noted=tuple(d.describe() for d in inspection.noted_defects),
        expires_on=inspection.expires_on(),
    )


@router.get("/{serial}/inspections")
def list_inspections(serial: str, work: Work) -> list[InspectionOut]:
    return [
        InspectionOut(
            device_serial=serial,
            performed_on=i.performed_on,
            is_quarterly=i.is_quarterly,
            passed=i.passed,
            blocking=tuple(d.describe() for d in i.blocking_defects),
            noted=tuple(d.describe() for d in i.noted_defects),
            expires_on=i.expires_on(),
        )
        for i in work.maintenance.for_device(serial)
    ]


@router.get("/{serial}/clearance")
def read_clearance(
    serial: str,
    work: Work,
    source: str,
    on: date | None = None,
    crew: str = "",
) -> ClearanceOut:
    day = on or date.today()
    found = clearance(work, serial, source, day, (), crew)
    return ClearanceOut(
        device_serial=serial,
        source_serial=source,
        day=day,
        cleared=found.cleared,
        source_reasons=found.source_reasons,
        device_reasons=found.device_reasons,
        crew_reasons=found.crew_reasons,
    )
