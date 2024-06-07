from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.safety.inventory import (
    INVENTORY_INTERVAL,
    DeviceUnit,
    Inventory,
    Sighting,
    Trip,
)
from qu_imager.sources.device import device_model
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import LeakTest, SealedSource, SourceCertificate

ASSAY = datetime(2024, 3, 1, tzinfo=UTC)
TODAY = date(2024, 6, 6)


def source(serial="IR-5512"):
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
    held = SealedSource(certificate=certificate)
    held.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return held


def stocked():
    inventory = Inventory()
    inventory.add_source(source())
    inventory.add_device(DeviceUnit("880D-221", device_model("880d")))
    return inventory


def test_a_source_at_the_shop_reads_as_being_at_the_shop():
    assert stocked().location_of("IR-5512", TODAY) == "shop"


def test_a_source_out_on_a_job_reads_as_being_at_the_site():
    inventory = stocked()
    inventory.send_out(Trip("IR-5512", "880D-221", "0142", "Come By Chance", date(2024, 6, 3)))
    assert inventory.location_of("IR-5512", TODAY) == "Come By Chance"
    assert inventory.open_trip("IR-5512") is not None


def test_a_source_that_came_back_reads_as_the_shop_again():
    inventory = stocked()
    inventory.send_out(Trip("IR-5512", "880D-221", "0142", "site", date(2024, 6, 3)))
    inventory.bring_back("IR-5512", date(2024, 6, 5))
    assert inventory.location_of("IR-5512", TODAY) == "shop"
    assert inventory.location_of("IR-5512", date(2024, 6, 4)) == "site"


def test_a_source_cannot_go_out_twice():
    inventory = stocked()
    inventory.send_out(Trip("IR-5512", "880D-221", "0142", "site", date(2024, 6, 3)))
    with pytest.raises(ConflictError):
        inventory.send_out(Trip("IR-5512", "880D-221", "0201", "other", date(2024, 6, 4)))


def test_a_source_that_is_not_out_cannot_come_back():
    inventory = stocked()
    with pytest.raises(ConflictError):
        inventory.bring_back("IR-5512", TODAY)


def test_a_source_cannot_come_back_before_it_went_out():
    inventory = stocked()
    inventory.send_out(Trip("IR-5512", "880D-221", "0142", "site", date(2024, 6, 3)))
    with pytest.raises(ValidationError):
        inventory.bring_back("IR-5512", date(2024, 6, 1))
    with pytest.raises(ValidationError):
        Trip("IR-5512", "880D-221", "0142", "site", date(2024, 6, 3), date(2024, 6, 1))


def test_a_source_or_device_not_on_the_inventory_cannot_be_sent_out():
    inventory = stocked()
    with pytest.raises(ValidationError):
        inventory.send_out(Trip("IR-9999", "880D-221", "0142", "site", TODAY))
    with pytest.raises(ValidationError):
        inventory.send_out(Trip("IR-5512", "880D-999", "0142", "site", TODAY))


def test_nothing_goes_on_the_inventory_twice():
    inventory = stocked()
    with pytest.raises(ConflictError):
        inventory.add_source(source())
    with pytest.raises(ConflictError):
        inventory.add_device(DeviceUnit("880D-221", device_model("880d")))


def test_a_source_never_sighted_and_never_out_is_unaccounted_for():
    inventory = stocked()
    assert inventory.unaccounted_on(TODAY) == ("IR-5512",)


def test_a_recent_sighting_accounts_for_it():
    inventory = stocked()
    inventory.sightings.append(Sighting("IR-5512", date(2024, 5, 1), "0142", "shop"))
    assert inventory.unaccounted_on(TODAY) == ()


def test_a_sighting_older_than_the_quarter_does_not():
    inventory = stocked()
    stale = TODAY - INVENTORY_INTERVAL - timedelta(days=1)
    inventory.sightings.append(Sighting("IR-5512", stale, "0142", "shop"))
    assert inventory.unaccounted_on(TODAY) == ("IR-5512",)


def test_a_source_out_on_a_job_is_accounted_for_without_a_sighting():
    inventory = stocked()
    inventory.send_out(Trip("IR-5512", "880D-221", "0142", "site", date(2024, 6, 3)))
    assert inventory.unaccounted_on(TODAY) == ()


def test_the_latest_sighting_is_the_one_that_counts():
    inventory = stocked()
    inventory.sightings.append(Sighting("IR-5512", date(2024, 1, 5), "0142", "shop"))
    inventory.sightings.append(Sighting("IR-5512", date(2024, 5, 5), "0201", "shop"))
    assert inventory.last_sighting("IR-5512").seen_by == "0201"
    assert inventory.unaccounted_on(TODAY) == ()


def test_exposures_are_totalled_by_crew():
    inventory = stocked()
    inventory.send_out(
        Trip("IR-5512", "880D-221", "0142", "site", date(2024, 5, 1), date(2024, 5, 2), 24)
    )
    inventory.send_out(
        Trip("IR-5512", "880D-221", "0142", "site", date(2024, 5, 3), date(2024, 5, 4), 18)
    )
    assert inventory.exposures_by("0142") == 42
    assert inventory.exposures_by("0201") == 0


def test_a_device_and_a_sighting_both_need_signing():
    with pytest.raises(ValidationError):
        DeviceUnit("  ", device_model("880d"))
    with pytest.raises(ValidationError):
        Sighting("IR-5512", TODAY, "   ", "shop")
    assert str(DeviceUnit("880D-221", device_model("880d"))) == "880 Delta 880D-221"
