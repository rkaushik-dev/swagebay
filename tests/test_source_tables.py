from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from qu_imager.persistence.base import Base
from qu_imager.persistence.models import (
    DeviceRow,
    LeakTestRow,
    SightingRow,
    SourceRow,
    TripRow,
)
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.length import Length, mm

ASSAY = datetime(2024, 3, 1)
WENT = date(2024, 6, 3)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def source_row(serial="IR-5512", activity=None, diameter=None):
    return SourceRow(
        serial=serial,
        isotope_key="ir192",
        assay_activity=activity if activity is not None else curie(90),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=diameter if diameter is not None else mm(3),
        active_length=mm(3),
        working_floor=curie(15),
    )


def test_a_source_round_trips_with_its_activity_and_pellet():
    with session() as db:
        db.add(source_row())
        db.commit()
        found = db.scalars(select(SourceRow)).one()
        assert found.assay_activity == curie(90)
        assert found.active_diameter == mm(3)
        assert found.state == "in service"


def test_two_sources_cannot_share_a_serial():
    with session() as db:
        db.add(source_row())
        db.add(source_row())
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_source_with_no_activity_or_no_pellet_is_refused():
    with session() as db:
        db.add(source_row(activity=Activity.ZERO))
        with pytest.raises(IntegrityError):
            db.commit()
    with session() as db:
        db.add(source_row(diameter=Length.ZERO))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_source_and_its_first_leak_test_go_in_together():
    with session() as db:
        source = source_row()
        source.leak_tests.append(
            LeakTestRow(performed_at=ASSAY, becquerels_removed=Decimal(0), technician="rp")
        )
        db.add(source)
        db.commit()
        found = db.scalars(select(LeakTestRow)).one()
        assert found.source.serial == "IR-5512"


def test_deleting_a_source_takes_its_leak_tests_with_it():
    with session() as db:
        source = source_row()
        source.leak_tests.append(
            LeakTestRow(performed_at=ASSAY, becquerels_removed=Decimal(0), technician="rp")
        )
        db.add(source)
        db.commit()
        db.delete(source)
        db.commit()
        assert db.scalars(select(LeakTestRow)).all() == []


def test_a_negative_wipe_result_is_refused():
    with session() as db:
        source = source_row()
        source.leak_tests.append(
            LeakTestRow(performed_at=ASSAY, becquerels_removed=Decimal(-1), technician="rp")
        )
        db.add(source)
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_trip_ties_a_source_to_a_device():
    with session() as db:
        source = source_row()
        device = DeviceRow(serial="880D-221", model_key="880d")
        db.add_all([source, device])
        db.flush()
        db.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="Come By Chance",
                went_out=WENT,
                exposures=24,
            )
        )
        db.commit()
        found = db.scalars(select(TripRow)).one()
        assert found.source.serial == "IR-5512"
        assert found.device.serial == "880D-221"
        assert found.came_back is None


def test_a_trip_cannot_come_back_before_it_went_out():
    with session() as db:
        source = source_row()
        device = DeviceRow(serial="880D-221", model_key="880d")
        db.add_all([source, device])
        db.flush()
        db.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="site",
                went_out=WENT,
                came_back=date(2024, 6, 1),
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_negative_exposure_count_is_refused():
    with session() as db:
        source = source_row()
        device = DeviceRow(serial="880D-221", model_key="880d")
        db.add_all([source, device])
        db.flush()
        db.add(
            TripRow(
                source_id=source.id,
                device_id=device.id,
                crew_badge="0142",
                site="site",
                went_out=WENT,
                exposures=-1,
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_two_devices_cannot_share_a_serial():
    with session() as db:
        db.add(DeviceRow(serial="880D-221", model_key="880d"))
        db.add(DeviceRow(serial="880D-221", model_key="880s"))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_sighting_hangs_off_the_source_it_sighted():
    with session() as db:
        source = source_row()
        source.sightings.append(
            SightingRow(seen_on=date(2024, 5, 1), seen_by="0142", location="shop")
        )
        db.add(source)
        db.commit()
        assert db.scalars(select(SightingRow)).one().source.serial == "IR-5512"


def test_a_source_can_hold_several_trips_and_they_come_back_with_it():
    with session() as db:
        source = source_row()
        device = DeviceRow(serial="880D-221", model_key="880d")
        db.add_all([source, device])
        db.flush()
        for day in (date(2024, 6, 3), date(2024, 6, 10)):
            db.add(
                TripRow(
                    source_id=source.id,
                    device_id=device.id,
                    crew_badge="0142",
                    site="site",
                    went_out=day,
                )
            )
        db.commit()
        db.refresh(source)
        assert len(source.trips) == 2
