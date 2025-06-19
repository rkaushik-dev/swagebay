"""Device inspection, which is the paperwork that decides whether a camera goes.

Two intervals, and they are different in kind.

The quarterly is a strip down. Every three months the device, the drive cable,
the guide tube, the connectors and the source changer come apart and get looked
at by somebody who is allowed to, and the record is kept for three years. Miss
it and the device is not out of tolerance, it is not authorised, which is a
different conversation and a shorter one.

The pre use check happens in the yard before the truck moves. It is a look and
a pull, it takes four minutes, and it is the one that actually catches things:
a frayed drive cable, a connector that will not seat, a lock that turns when it
should not, a label nobody can read any more.

The rule that matters is what a defect does. A defect on a safety item stops
the device that morning. A defect on something else is written down and worked
around, and the difference is decided by the item, not by whoever is in a
hurry. So the item table carries the classification and the person filling in
the form does not get a say.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from qu_imager.errors import ValidationError

QUARTERLY_DAYS = 92
RECORD_RETENTION_DAYS = 3 * 365


class Severity(Enum):
    """What a defect on this item does to the day."""

    STOPS_THE_DEVICE = "stops the device"
    WORK_AROUND = "work around"

    def __str__(self) -> str:
        return self.value


class Item(Enum):
    """The things that get looked at, and what a fault on each one means."""

    DRIVE_CABLE = ("drive cable", Severity.STOPS_THE_DEVICE)
    GUIDE_TUBE = ("guide tube", Severity.STOPS_THE_DEVICE)
    CONNECTORS = ("connectors", Severity.STOPS_THE_DEVICE)
    SOURCE_LOCK = ("source lock", Severity.STOPS_THE_DEVICE)
    SELECTOR_RING = ("selector ring", Severity.STOPS_THE_DEVICE)
    CRANK_ASSEMBLY = ("crank assembly", Severity.STOPS_THE_DEVICE)
    LABELS = ("labels", Severity.WORK_AROUND)
    CARRY_HANDLE = ("carry handle", Severity.WORK_AROUND)
    CASE_AND_SEALS = ("case and seals", Severity.WORK_AROUND)
    WHEELS = ("wheels", Severity.WORK_AROUND)

    def __init__(self, label: str, severity: Severity) -> None:
        self.label = label
        self.severity = severity

    @property
    def stops_the_device(self) -> bool:
        return self.severity is Severity.STOPS_THE_DEVICE

    def __str__(self) -> str:
        return self.label


def safety_items() -> tuple[Item, ...]:
    return tuple(item for item in Item if item.stops_the_device)


@dataclass(frozen=True, slots=True)
class Defect:
    """One thing wrong, on one item."""

    item: Item
    account: str

    def __post_init__(self) -> None:
        if not self.account.strip():
            raise ValidationError("a defect says what is wrong with it", item=str(self.item))

    @property
    def stops_the_device(self) -> bool:
        return self.item.stops_the_device

    def describe(self) -> str:
        return f"{self.item}: {self.account}"


@dataclass(frozen=True, slots=True)
class Inspection:
    """One inspection of one device, quarterly or before use."""

    device_serial: str
    performed_on: date
    performed_by: str
    is_quarterly: bool
    items_checked: tuple[Item, ...]
    defects: tuple[Defect, ...] = ()
    notes: str = ""

    def __post_init__(self) -> None:
        if not self.device_serial.strip():
            raise ValidationError("an inspection names the device")
        if not self.performed_by.strip():
            raise ValidationError("an inspection is signed")
        if not self.items_checked:
            raise ValidationError("an inspection that checked nothing is not an inspection")
        if len(set(self.items_checked)) != len(self.items_checked):
            raise ValidationError("that item is on the sheet twice")
        unchecked = [d.item for d in self.defects if d.item not in self.items_checked]
        if unchecked:
            raise ValidationError(
                "a defect was raised on an item nobody checked",
                item=str(unchecked[0]),
            )
        if self.is_quarterly:
            missed = [item for item in Item if item not in self.items_checked]
            if missed:
                raise ValidationError(
                    "a quarterly covers every item on the device",
                    missed=", ".join(str(item) for item in missed),
                )

    @property
    def passed(self) -> bool:
        return not self.blocking_defects

    @property
    def blocking_defects(self) -> tuple[Defect, ...]:
        return tuple(d for d in self.defects if d.stops_the_device)

    @property
    def noted_defects(self) -> tuple[Defect, ...]:
        return tuple(d for d in self.defects if not d.stops_the_device)

    def expires_on(self) -> date | None:
        """A quarterly runs out. A pre use check covers the day it was done."""
        if not self.is_quarterly:
            return None
        return self.performed_on + timedelta(days=QUARTERLY_DAYS)

    def destroy_after(self) -> date:
        return self.performed_on + timedelta(days=RECORD_RETENTION_DAYS)

    def describe(self) -> str:
        kind = "quarterly" if self.is_quarterly else "pre use"
        verdict = "passed" if self.passed else "failed"
        return f"{self.performed_on} {self.device_serial} {kind} {verdict} by {self.performed_by}"


def pre_use(
    device_serial: str, on: date, by: str, defects: tuple[Defect, ...] = (), notes: str = ""
) -> Inspection:
    """The yard check, which covers the safety items and nothing else."""
    return Inspection(
        device_serial=device_serial,
        performed_on=on,
        performed_by=by,
        is_quarterly=False,
        items_checked=safety_items(),
        defects=defects,
        notes=notes,
    )


def quarterly(
    device_serial: str, on: date, by: str, defects: tuple[Defect, ...] = (), notes: str = ""
) -> Inspection:
    """The strip down, which covers everything."""
    return Inspection(
        device_serial=device_serial,
        performed_on=on,
        performed_by=by,
        is_quarterly=True,
        items_checked=tuple(Item),
        defects=defects,
        notes=notes,
    )


@dataclass(slots=True)
class MaintenanceRegister:
    """Every inspection, and the one question asked of it every morning."""

    inspections: list[Inspection] = field(default_factory=list)

    def record(self, inspection: Inspection) -> None:
        self.inspections.append(inspection)

    def for_device(self, serial: str) -> tuple[Inspection, ...]:
        found = [i for i in self.inspections if i.device_serial == serial]
        return tuple(sorted(found, key=lambda i: i.performed_on))

    def latest_quarterly(self, serial: str) -> Inspection | None:
        found = [i for i in self.for_device(serial) if i.is_quarterly]
        return found[-1] if found else None

    def check_on(self, serial: str, day: date) -> Inspection | None:
        for inspection in self.for_device(serial):
            if not inspection.is_quarterly and inspection.performed_on == day:
                return inspection
        return None

    def reasons_not_to_use(self, serial: str, day: date) -> tuple[str, ...]:
        """Everything stopping this device going out that morning."""
        reasons: list[str] = []
        latest = self.latest_quarterly(serial)
        if latest is None:
            reasons.append("no quarterly inspection on record")
        else:
            expires = latest.expires_on()
            if expires is not None and day >= expires:
                reasons.append(f"quarterly ran out on {expires}")
            elif latest.blocking_defects:
                reasons.append(f"last quarterly found {latest.blocking_defects[0].describe()}")
        check = self.check_on(serial, day)
        if check is None:
            reasons.append("no pre use check that morning")
        elif check.blocking_defects:
            reasons.append(check.blocking_defects[0].describe())
        return tuple(reasons)

    def may_go_out(self, serial: str, day: date) -> bool:
        return not self.reasons_not_to_use(serial, day)

    def quarterly_due_by(self, serial: str) -> date | None:
        latest = self.latest_quarterly(serial)
        return latest.expires_on() if latest is not None else None

    def overdue_on(self, serials: tuple[str, ...], day: date) -> tuple[str, ...]:
        found = []
        for serial in serials:
            due = self.quarterly_due_by(serial)
            if due is None or day >= due:
                found.append(serial)
        return tuple(sorted(found))
