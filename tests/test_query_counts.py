"""How many round trips the listings take, which is a thing a test can hold.

Nothing here measures time. A timing assertion on a laptop is a flaky test
wearing a suit. What is stable is the number of statements, and the defects
worth catching are the ones where a listing goes from a fixed number of queries
to one per row without anybody noticing on a database with four rows in it.

The counts below are ceilings and they are deliberately loose. They are there
to fail when a listing starts scaling with the fleet, not to pin the exact
statement count of an orm that is allowed to change its mind.
"""

from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import event

from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.services.dispatch import cleared_devices
from qu_imager.services.morning import loads_ready, sheet_for
from qu_imager.services.sources import register_device, register_source
from qu_imager.services.wipes import record_wipe
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import SealedSource, SourceCertificate

from conftest import database, pass_inspections

ASSAY = datetime(2025, 7, 1)
DAY = date(2025, 7, 22)


class Counter:
    def __init__(self) -> None:
        self.statements = 0

    def __call__(self, conn, cursor, statement, parameters, context, executemany) -> None:
        if statement.lstrip().upper().startswith("SELECT"):
            self.statements += 1


def counted(work):
    counter = Counter()
    event.listen(work.session.get_bind(), "before_cursor_execute", counter)
    return counter


def a_source(serial):
    return SealedSource(
        certificate=SourceCertificate(
            serial=serial,
            isotope=IR192,
            assay_activity=curie(90),
            assay_at=ASSAY,
            manufacturer="QSA",
            model="A424-9",
            active_diameter=mm(3),
            active_length=mm(3),
        ),
        working_floor=curie(15),
    )


@pytest.fixture
def yard():
    work = UnitOfWork(database())
    work.__enter__()
    for index in range(1, 7):
        serial = f"D-{index:02d}"
        register_device(work, serial, "880d")
        pass_inspections(work, serial, DAY)
    for index in range(1, 5):
        serial = f"IR-{index:04d}"
        register_source(work, a_source(serial))
        record_wipe(work, serial, ASSAY, Decimal(0), "rp")
    work.flush()
    yield work
    work.__exit__(None, None, None)


def test_the_morning_sheet_does_not_read_once_per_device(yard):
    counter = counted(yard)
    sheet_for(yard, DAY)
    assert counter.statements <= 10


def test_the_ready_loads_do_not_read_once_per_pairing(yard):
    counter = counted(yard)
    pairings = loads_ready(yard, DAY)
    assert len(pairings) == 24
    assert counter.statements <= 40


def test_the_cleared_devices_read_the_register_once(yard):
    counter = counted(yard)
    assert len(cleared_devices(yard, "IR-0001", DAY)) == 6
    assert counter.statements <= 20


def test_the_counts_do_not_grow_with_the_fleet(yard):
    before = counted(yard)
    sheet_for(yard, DAY)
    first = before.statements
    for index in range(7, 13):
        serial = f"D-{index:02d}"
        register_device(yard, serial, "880d")
        pass_inspections(yard, serial, DAY)
    yard.flush()
    after = counted(yard)
    sheet_for(yard, DAY)
    assert after.statements <= first + 2
