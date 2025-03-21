from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from qu_imager.persistence.base import Base, table_names
from qu_imager.persistence.models import (
    CalibrationRow,
    ControlStripRow,
    FiledRow,
    IncidentRow,
    LotRow,
    QualificationRow,
)
from qu_imager.quantities.length import Length, mm

DAY = date(2025, 3, 20)
MOMENT = datetime(2025, 3, 20, 23, 30)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def incident(reference="INC-1", notified=None):
    return IncidentRow(
        reference=reference,
        occurrence="dose over a limit",
        discovered_at=MOMENT,
        site="Come By Chance",
        reported_by="0142",
        account="the source hung up",
        notified_at=notified,
    )


def calibration(serial="LU-3-1188", due=date(2026, 3, 20)):
    return CalibrationRow(
        serial=serial,
        instrument_kind="survey meter",
        calibrated_on=DAY,
        due_on=due,
        laboratory="NRC",
        certificate_reference="C-1188",
    )


def lot(number="L-1", sheets=100, expires=date(2026, 3, 20)):
    return LotRow(
        lot_number=number,
        film_key="d7",
        size="4x10",
        sheets=sheets,
        received_on=DAY,
        expires_on=expires,
    )


def qualification(welder="w-19", coupon=None):
    return QualificationRow(
        welder=welder,
        process="SMAW",
        position="6G",
        coupon=coupon if coupon is not None else mm(12),
        material_group="P1",
        qualified_on=DAY,
        last_used_on=DAY,
    )


def test_every_register_is_on_the_metadata():
    names = table_names()
    for expected in ("incident", "calibration", "filed", "lot", "qualification", "control_strip"):
        assert expected in names


def test_an_incident_round_trips():
    with session() as db:
        db.add(incident())
        db.commit()
        found = db.scalars(select(IncidentRow)).one()
        assert found.reference == "INC-1"
        assert found.notified_at is None


def test_an_incident_cannot_be_reported_before_it_was_found():
    with session() as db:
        db.add(incident(notified=MOMENT - __import__("datetime").timedelta(hours=1)))
        with pytest.raises(IntegrityError):
            db.commit()


def test_an_incident_reference_is_used_once():
    with session() as db:
        db.add(incident())
        db.add(incident())
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_certificate_expires_after_it_was_issued():
    with session() as db:
        db.add(calibration(due=DAY))
        with pytest.raises(IntegrityError):
            db.commit()


def test_the_same_instrument_can_hold_several_certificates():
    with session() as db:
        db.add(calibration(due=date(2026, 3, 20)))
        db.add(calibration(due=date(2027, 3, 20)))
        db.commit()
        assert len(db.scalars(select(CalibrationRow)).all()) == 2


def test_a_filed_radiograph_may_have_no_location_yet():
    with session() as db:
        db.add(
            FiledRow(
                radiograph="25-0001-R0001", job="25-0001", filed_on=DAY, retention="five years"
            )
        )
        db.commit()
        found = db.scalars(select(FiledRow)).one()
        assert found.site is None
        assert found.box is None


def test_a_radiograph_is_filed_once():
    with session() as db:
        for _ in range(2):
            db.add(
                FiledRow(
                    radiograph="25-0001-R0001",
                    job="25-0001",
                    filed_on=DAY,
                    retention="five years",
                )
            )
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_lot_cannot_hold_a_negative_number_of_sheets():
    with session() as db:
        db.add(lot(sheets=-1))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_lot_expires_after_it_arrived():
    with session() as db:
        db.add(lot(expires=DAY))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_lot_number_is_used_once():
    with session() as db:
        db.add(lot("L-1"))
        db.add(lot("L-1"))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_qualification_needs_a_coupon_with_a_thickness():
    with session() as db:
        db.add(qualification(coupon=Length.ZERO))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_welder_qualifies_on_one_process_once_a_day():
    with session() as db:
        db.add(qualification())
        db.add(qualification())
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_qualification_keeps_its_coupon_as_a_length():
    with session() as db:
        db.add(qualification())
        db.commit()
        assert db.scalars(select(QualificationRow)).one().coupon == mm(12)


def test_a_control_strip_needs_believable_readings():
    with session() as db:
        db.add(
            ControlStripRow(
                processor="P-1",
                read_on=DAY,
                speed=Decimal(0),
                contrast=Decimal(3),
                fog=Decimal("0.18"),
                operator="dr",
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_one_strip_a_processor_a_day():
    def strip():
        return ControlStripRow(
            processor="P-1",
            read_on=DAY,
            speed=Decimal(2),
            contrast=Decimal(3),
            fog=Decimal("0.18"),
            operator="dr",
        )

    with session() as db:
        db.add(strip())
        db.add(strip())
        with pytest.raises(IntegrityError):
            db.commit()
