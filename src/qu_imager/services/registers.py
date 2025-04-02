"""Writing to the five registers, which is the same shape five times over.

Each of these takes a domain object, turns it into a row and flushes. There is
no clever base class doing it for them and there was one for a while. It went
because the base class needed a mapper argument, a table argument and a
uniqueness check that differed for every register, at which point it was a
worse way of writing five short functions than five short functions.

What they do share is the refusal to overwrite. Every one of them checks for
the key first and raises rather than updating, because a register is append
only and a silent update in an append only register is the defect that cannot
be found afterwards.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from qu_imager.calibration import Certificate, Instrument
from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.models.registers import (
    CalibrationRow,
    FiledRow,
    IncidentRow,
    LotRow,
    QualificationRow,
)
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.reporting.archive import Filed
from qu_imager.safety.incidents import Incident
from qu_imager.stock import Lot
from qu_imager.welding import Qualification


def record_incident(work: UnitOfWork, incident: Incident) -> None:
    if work.incidents.find(incident.reference) is not None:
        raise ConflictError("that reference is already used", reference=incident.reference)
    work.session.add(
        IncidentRow(
            reference=incident.reference,
            occurrence=incident.occurrence.value,
            discovered_at=incident.discovered_at,
            site=incident.site,
            reported_by=incident.reported_by,
            account=incident.account,
            notified_at=incident.notified_at,
        )
    )
    work.flush()


def notify_incident(work: UnitOfWork, reference: str, at: datetime) -> None:
    row = work.incidents.get(reference)
    if row.notified_at is not None:
        raise ConflictError("that incident is already reported", reference=reference)
    if at < row.discovered_at:
        raise ValidationError("an incident cannot be reported before it was found")
    row.notified_at = at
    work.flush()


def record_calibration(
    work: UnitOfWork, serial: str, kind: Instrument, certificate: Certificate
) -> None:
    if not serial.strip():
        raise ValidationError("an instrument has a serial number")
    latest = work.calibrations.latest_for(serial)
    if latest is not None and latest.calibrated_on == certificate.calibrated_on:
        raise ConflictError("that calibration is already recorded", serial=serial)
    work.session.add(
        CalibrationRow(
            serial=serial,
            instrument_kind=kind.value,
            calibrated_on=certificate.calibrated_on,
            due_on=certificate.due_on,
            laboratory=certificate.laboratory,
            certificate_reference=certificate.reference,
        )
    )
    work.flush()


def receive_lot(work: UnitOfWork, lot: Lot) -> None:
    existing = work.stock.usable_on(lot.received_on, lot.film_key, lot.size)
    if any(row.lot_number == lot.lot_number for row in existing):
        raise ConflictError("that lot is already on the shelf", lot=lot.lot_number)
    work.session.add(
        LotRow(
            lot_number=lot.lot_number,
            film_key=lot.film_key,
            size=lot.size,
            sheets=lot.sheets,
            received_on=lot.received_on,
            expires_on=lot.expires_on,
        )
    )
    work.flush()


def issue_film(
    work: UnitOfWork, day: date, film_key: str, size: str, sheets: int
) -> tuple[tuple[str, int], ...]:
    """Take sheets off the shelf shortest dated first, and say from where."""
    if sheets <= 0:
        raise ValidationError("an issue is at least one sheet", sheets=sheets)
    available = work.stock.usable_on(day, film_key, size)
    on_hand = sum(row.sheets for row in available)
    if on_hand < sheets:
        raise ConflictError(
            "there is not enough film on the shelf",
            film=film_key,
            size=size,
            wanted=sheets,
            on_hand=on_hand,
        )
    taken: list[tuple[str, int]] = []
    remaining = sheets
    for row in available:
        if remaining == 0:
            break
        take = min(row.sheets, remaining)
        row.sheets -= take
        remaining -= take
        taken.append((row.lot_number, take))
    work.flush()
    return tuple(taken)


def file_radiograph(work: UnitOfWork, filed: Filed) -> None:
    if work.archive.find(filed.radiograph) is not None:
        raise ConflictError("that radiograph is already filed", radiograph=filed.radiograph)
    where = filed.location
    work.session.add(
        FiledRow(
            radiograph=filed.radiograph,
            job=filed.job,
            filed_on=filed.filed_on,
            retention=filed.retention.value,
            site=where.site if where is not None else None,
            cabinet=where.cabinet if where is not None else None,
            box=where.box if where is not None else None,
        )
    )
    work.flush()


def record_qualification(work: UnitOfWork, qualification: Qualification) -> None:
    held = work.qualifications.for_welder(qualification.welder)
    if any(
        row.process == qualification.process.name and row.qualified_on == qualification.qualified_on
        for row in held
    ):
        raise ConflictError("that qualification is already recorded", welder=qualification.welder)
    work.session.add(
        QualificationRow(
            welder=qualification.welder,
            process=qualification.process.name,
            position=qualification.position.value,
            coupon=qualification.coupon,
            material_group=qualification.material_group,
            qualified_on=qualification.qualified_on,
            last_used_on=qualification.qualified_on,
        )
    )
    work.flush()


def record_process_use(work: UnitOfWork, welder: str, process: str, on: date) -> None:
    rows = [r for r in work.qualifications.for_welder(welder) if r.process == process]
    if not rows:
        raise ConflictError("that welder is not qualified for that process", welder=welder)
    for row in rows:
        if on > row.last_used_on:
            row.last_used_on = on
    work.flush()


def sheets_on_hand(work: UnitOfWork, day: date, film_key: str, size: str) -> int:
    return work.stock.on_hand(day, film_key, size)


def reorder_needed(work: UnitOfWork, day: date, film_key: str, size: str, floor: int) -> bool:
    if floor < 0:
        raise ValidationError("a reorder floor is not negative", floor=floor)
    return sheets_on_hand(work, day, film_key, size) <= floor


def strip_speed(value: str) -> Decimal:
    """Small helper so a caller can hand a control strip reading in as text."""
    return Decimal(value)
