from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.personnel import Level, LicenceRole, Person
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.safety.maintenance import Defect, Item, pre_use, quarterly
from qu_imager.services.dispatch import clearance, cleared_devices, require_clearance
from qu_imager.services.sources import register_device, register_source
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import LeakTest, SealedSource, SourceCertificate

from conftest import database, pass_inspections

ASSAY = datetime(2025, 5, 1)
DAY = date(2025, 6, 23)
CABLE = Defect(Item.DRIVE_CABLE, "three strands broken")


def a_source(serial="IR-5512", wiped=True):
    certificate = SourceCertificate(
        serial=serial,
        isotope=IR192,
        assay_activity=curie(90),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
    )
    source = SealedSource(certificate=certificate, working_floor=curie(15))
    if wiped:
        source.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return source


def a_person(badge="0142", vision=date(2025, 3, 1)):
    return Person(
        badge=badge,
        name="crew",
        level=Level.LEVEL_II,
        licence_role=LicenceRole.RADIOGRAPHER,
        certified_on=date(2024, 1, 15),
        near_vision_on=vision,
        colour_perception_on=vision,
    )


def stocked(work, wiped=True, inspected=True):
    register_source(work, a_source(wiped=wiped))
    register_device(work, "D-77", "880d")
    if inspected:
        pass_inspections(work, "D-77", DAY)


def test_a_load_with_everything_in_order_is_cleared():
    with transaction(database()) as work:
        stocked(work)
        found = clearance(work, "D-77", "IR-5512", DAY, (a_person(),))
    assert found.cleared
    assert "is clear for" in found.describe()


def test_a_source_nobody_registered_stops_it():
    with transaction(database()) as work:
        register_device(work, "D-77", "880d")
        pass_inspections(work, "D-77", DAY)
        found = clearance(work, "D-77", "IR-9999", DAY, (a_person(),))
    assert not found.cleared
    assert "not on the inventory" in found.source_reasons[0]


def test_a_device_nobody_registered_stops_it():
    with transaction(database()) as work:
        register_source(work, a_source())
        found = clearance(work, "D-99", "IR-5512", DAY, (a_person(),))
    assert "not on the inventory" in found.device_reasons[0]


def test_a_source_with_a_stale_wipe_test_stops_it():
    with transaction(database()) as work:
        stocked(work, wiped=False)
        found = clearance(work, "D-77", "IR-5512", DAY, (a_person(),))
    assert any("leak test" in reason for reason in found.source_reasons)


def test_a_device_nobody_inspected_stops_it():
    with transaction(database()) as work:
        stocked(work, inspected=False)
        found = clearance(work, "D-77", "IR-5512", DAY, (a_person(),))
    assert any("no quarterly" in reason for reason in found.device_reasons)


def test_a_broken_drive_cable_that_morning_stops_it():
    with transaction(database()) as work:
        stocked(work, inspected=False)
        work.maintenance.record(quarterly("D-77", DAY - timedelta(days=30), "0303"))
        work.maintenance.record(pre_use("D-77", DAY, "0142", (CABLE,)))
        found = clearance(work, "D-77", "IR-5512", DAY, (a_person(),))
    assert any("drive cable" in reason for reason in found.device_reasons)


def test_a_crew_whose_eyes_have_lapsed_stops_it():
    with transaction(database()) as work:
        stocked(work)
        stale = a_person(vision=date(2023, 1, 1))
        found = clearance(work, "D-77", "IR-5512", DAY, (stale,))
    assert found.crew_reasons and "0142" in found.crew_reasons[0]


def test_a_booking_with_nobody_on_it_stops_it():
    with transaction(database()) as work:
        stocked(work)
        found = clearance(work, "D-77", "IR-5512", DAY)
    assert found.crew_reasons == ("nobody is named on the booking",)


def test_a_badge_alone_is_enough_to_have_somebody_named():
    with transaction(database()) as work:
        stocked(work)
        found = clearance(work, "D-77", "IR-5512", DAY, (), "0142")
    assert found.cleared


def test_every_reason_comes_back_not_the_first():
    with transaction(database()) as work:
        stocked(work, wiped=False, inspected=False)
        found = clearance(work, "D-77", "IR-5512", DAY)
    assert len(found.reasons) >= 4
    assert found.reasons[0].startswith("source")
    assert found.reasons[-1] == "nobody is named on the booking"


def test_the_gate_as_a_refusal_names_every_reason():
    with transaction(database()) as work:
        stocked(work, inspected=False)
        with pytest.raises(ConflictError) as caught:
            require_clearance(work, "D-77", "IR-5512", DAY, (a_person(),))
    assert "no quarterly" in caught.value.context["reasons"]


def test_the_gate_lets_a_good_load_through():
    with transaction(database()) as work:
        stocked(work)
        assert require_clearance(work, "D-77", "IR-5512", DAY, (a_person(),)).cleared


def test_the_cameras_this_source_could_go_out_in_can_be_listed():
    with transaction(database()) as work:
        stocked(work)
        register_device(work, "D-78", "880s")
        assert cleared_devices(work, "IR-5512", DAY) == ("D-77",)


def test_a_shop_with_no_devices_at_all_is_a_not_found():
    from qu_imager.errors import NotFoundError

    with transaction(database()) as work:
        register_source(work, a_source())
        with pytest.raises(NotFoundError):
            cleared_devices(work, "IR-5512", DAY)
