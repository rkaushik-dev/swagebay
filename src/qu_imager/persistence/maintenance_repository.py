"""Reading and writing inspections, and handing back a register to ask.

The register is a domain object with the whole fleet's history in it, and it is
built here from rows for one device or one set of devices. Building it for the
devices actually being asked about rather than for everything is the difference
between a morning check that reads six rows and one that reads six thousand by
the end of the second year.
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import ValidationError
from qu_imager.persistence.models.maintenance import DefectRow, InspectionRow
from qu_imager.safety.maintenance import Defect, Inspection, Item, MaintenanceRegister

SEPARATOR = ","


def _item_named(key: str) -> Item:
    try:
        return Item[key]
    except KeyError:
        raise ValidationError("nothing here inspects that", item=key) from None


def row_to_inspection(row: InspectionRow) -> Inspection:
    items = tuple(_item_named(key) for key in row.items_checked.split(SEPARATOR) if key)
    return Inspection(
        device_serial=row.device_serial,
        performed_on=row.performed_on,
        performed_by=row.performed_by,
        is_quarterly=row.is_quarterly,
        items_checked=items,
        defects=tuple(Defect(_item_named(d.item_key), d.account) for d in row.defects),
        notes=row.notes,
    )


def inspection_to_row(inspection: Inspection) -> InspectionRow:
    row = InspectionRow(
        device_serial=inspection.device_serial,
        performed_on=inspection.performed_on,
        performed_by=inspection.performed_by,
        is_quarterly=inspection.is_quarterly,
        items_checked=SEPARATOR.join(item.name for item in inspection.items_checked),
        notes=inspection.notes,
    )
    for defect in inspection.defects:
        row.defects.append(DefectRow(item_key=defect.item.name, account=defect.account))
    return row


class MaintenanceRepository:
    """Inspections in, and a register out."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def record(self, inspection: Inspection) -> InspectionRow:
        row = inspection_to_row(inspection)
        self.session.add(row)
        return row

    def for_device(self, serial: str) -> tuple[Inspection, ...]:
        rows = self.session.scalars(
            select(InspectionRow)
            .where(InspectionRow.device_serial == serial)
            .order_by(InspectionRow.performed_on, InspectionRow.id)
        ).all()
        return tuple(row_to_inspection(row) for row in rows)

    def register_for(self, serials: tuple[str, ...]) -> MaintenanceRegister:
        if not serials:
            return MaintenanceRegister()
        rows = self.session.scalars(
            select(InspectionRow)
            .where(InspectionRow.device_serial.in_(serials))
            .order_by(InspectionRow.performed_on, InspectionRow.id)
        ).all()
        register = MaintenanceRegister()
        for row in rows:
            register.record(row_to_inspection(row))
        return register

    def defects_on(self, item: Item) -> tuple[tuple[str, str], ...]:
        """Every device that has ever had a fault on one item, with the account."""
        rows = self.session.execute(
            select(InspectionRow.device_serial, DefectRow.account)
            .join(DefectRow, DefectRow.inspection_id == InspectionRow.id)
            .where(DefectRow.item_key == item.name)
            .order_by(InspectionRow.performed_on, InspectionRow.id)
        ).all()
        return tuple((serial, account) for serial, account in rows)
