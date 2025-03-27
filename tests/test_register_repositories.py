from datetime import date, datetime, timedelta

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from qu_imager.errors import NotFoundError, ValidationError
from qu_imager.persistence.base import Base
from qu_imager.persistence.models import (
    CalibrationRow,
    FiledRow,
    IncidentRow,
    LotRow,
    QualificationRow,
)
from qu_imager.persistence.register_repositories import (
    ArchiveRepository,
    CalibrationRepository,
    IncidentRepository,
    QualificationRepository,
    StockRepository,
)
from qu_imager.quantities.length import mm

DAY = date(2025, 3, 24)
MOMENT = datetime(2025, 3, 24, 23, 30)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def incident(reference="INC-1", notified=None, found=MOMENT):
    return IncidentRow(
        reference=reference,
        occurrence="dose over a limit",
        discovered_at=found,
        site="site",
        reported_by="0142",
        account="a",
        notified_at=notified,
    )


def calibration(serial="LU-1", done=DAY, due=None):
    return CalibrationRow(
        serial=serial,
        instrument_kind="survey meter",
        calibrated_on=done,
        due_on=due or done + timedelta(days=365),
        laboratory="NRC",
        certificate_reference="C-1",
    )


def lot(number="L-1", sheets=100, expires_in=200, film="d7", size="4x10"):
    return LotRow(
        lot_number=number,
        film_key=film,
        size=size,
        sheets=sheets,
        received_on=DAY - timedelta(days=30),
        expires_on=DAY + timedelta(days=expires_in),
    )


def filed(radiograph="25-0001-R0001", job="25-0001", located=True):
    return FiledRow(
        radiograph=radiograph,
        job=job,
        filed_on=DAY,
        retention="five years",
        site="site" if located else None,
        cabinet="A" if located else None,
        box="14" if located else None,
    )


def qualification(welder="w-19", process="SMAW", last_used=DAY):
    return QualificationRow(
        welder=welder,
        process=process,
        position="6G",
        coupon=mm(12),
        material_group="P1",
        qualified_on=DAY - timedelta(days=400),
        last_used_on=last_used,
    )


def test_an_unreported_incident_is_outstanding_and_a_reported_one_is_not():
    with session() as db:
        db.add(incident("INC-1"))
        db.add(incident("INC-2", notified=MOMENT))
        db.commit()
        assert [row.reference for row in IncidentRepository(db).unreported()] == ["INC-1"]


def test_the_unreported_list_comes_back_oldest_first():
    with session() as db:
        db.add(incident("INC-2", found=MOMENT))
        db.add(incident("INC-1", found=MOMENT - timedelta(hours=2)))
        db.commit()
        assert [r.reference for r in IncidentRepository(db).unreported()] == ["INC-1", "INC-2"]


def test_an_incident_is_found_by_reference_and_a_missing_one_raises():
    with session() as db:
        db.add(incident())
        db.commit()
        repository = IncidentRepository(db)
        assert repository.get("INC-1").site == "site"
        assert repository.find("INC-9") is None
        with pytest.raises(NotFoundError):
            repository.get("INC-9")


def test_incidents_since_a_moment_exclude_the_older_ones():
    with session() as db:
        db.add(incident("INC-1", found=MOMENT - timedelta(days=5)))
        db.add(incident("INC-2", found=MOMENT))
        db.commit()
        found = IncidentRepository(db).since(MOMENT - timedelta(days=1))
        assert [row.reference for row in found] == ["INC-2"]


def test_the_latest_certificate_is_the_one_that_counts():
    with session() as db:
        db.add(calibration(done=DAY - timedelta(days=400)))
        db.add(calibration(done=DAY))
        db.commit()
        assert CalibrationRepository(db).latest_for("LU-1").calibrated_on == DAY


def test_an_instrument_with_an_expired_certificate_is_listed():
    with session() as db:
        db.add(calibration("LU-1", done=DAY - timedelta(days=400)))
        db.add(calibration("LU-2", done=DAY))
        db.commit()
        assert CalibrationRepository(db).expired_on(DAY) == ("LU-1",)


def test_the_look_ahead_names_what_can_still_be_booked():
    with session() as db:
        db.add(calibration("LU-1", done=DAY - timedelta(days=350)))
        db.commit()
        repository = CalibrationRepository(db)
        assert repository.due_within(DAY, 30) == ("LU-1",)
        assert repository.due_within(DAY, 5) == ()
        with pytest.raises(ValidationError):
            repository.due_within(DAY, -1)


def test_film_comes_off_the_shelf_shortest_dated_first():
    with session() as db:
        db.add(lot("L-1", expires_in=300))
        db.add(lot("L-2", expires_in=100))
        db.commit()
        found = StockRepository(db).usable_on(DAY, "d7", "4x10")
        assert [row.lot_number for row in found] == ["L-2", "L-1"]


def test_expired_and_empty_lots_are_not_on_hand():
    with session() as db:
        db.add(lot("L-1", expires_in=-1))
        db.add(lot("L-2", sheets=0))
        db.add(lot("L-3", sheets=40))
        db.commit()
        assert StockRepository(db).on_hand(DAY, "d7", "4x10") == 40


def test_the_expired_list_only_names_lots_with_film_in_them():
    with session() as db:
        db.add(lot("L-1", expires_in=-1))
        db.add(lot("L-2", sheets=0, expires_in=-1))
        db.commit()
        assert StockRepository(db).expired_on(DAY) == ("L-1",)


def test_film_of_another_size_is_not_on_hand():
    with session() as db:
        db.add(lot("L-1", size="7x17"))
        db.commit()
        assert StockRepository(db).on_hand(DAY, "d7", "4x10") == 0


def test_the_films_on_one_job_come_back_in_order():
    with session() as db:
        db.add(filed("25-0001-R0002"))
        db.add(filed("25-0001-R0001"))
        db.add(filed("25-0002-R0001", job="25-0002"))
        db.commit()
        found = ArchiveRepository(db).for_job("25-0001")
        assert [row.radiograph for row in found] == ["25-0001-R0001", "25-0001-R0002"]


def test_a_film_with_no_location_is_reported():
    with session() as db:
        db.add(filed("25-0001-R0001", located=False))
        db.add(filed("25-0001-R0002"))
        db.commit()
        repository = ArchiveRepository(db)
        assert repository.without_a_location() == ("25-0001-R0001",)
        assert repository.find("25-0001-R0002").box == "14"


def test_a_welder_who_has_gone_quiet_is_lapsed():
    with session() as db:
        db.add(qualification("w-19", last_used=DAY - timedelta(days=200)))
        db.add(qualification("w-04", last_used=DAY - timedelta(days=10)))
        db.commit()
        assert QualificationRepository(db).lapsed_on(DAY) == (("w-19", "SMAW"),)


def test_the_continuity_window_is_an_argument():
    with session() as db:
        db.add(qualification("w-19", last_used=DAY - timedelta(days=100)))
        db.commit()
        repository = QualificationRepository(db)
        assert repository.lapsed_on(DAY) == ()
        assert repository.lapsed_on(DAY, continuity_days=90) == (("w-19", "SMAW"),)
        with pytest.raises(ValidationError):
            repository.lapsed_on(DAY, continuity_days=0)


def test_every_qualification_a_welder_holds_comes_back():
    with session() as db:
        db.add(qualification("w-19", process="SMAW"))
        db.add(qualification("w-19", process="GTAW"))
        db.commit()
        assert len(QualificationRepository(db).for_welder("w-19")) == 2
        assert QualificationRepository(db).for_welder("w-99") == ()
