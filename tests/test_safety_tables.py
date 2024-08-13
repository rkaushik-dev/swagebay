from datetime import date, datetime

import pytest
from sqlalchemy import create_engine, func, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from qu_imager.persistence.base import Base, table_names
from qu_imager.persistence.models import (
    DoseEntryRow,
    DosimeterRow,
    SurveyPointRow,
    SurveyRow,
)
from qu_imager.quantities.dose import Dose, usv, usv_per_hour
from qu_imager.quantities.length import Length

WHEN = datetime(2024, 8, 13, 23, 30)
DUE = datetime(2024, 12, 1)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def survey_row(stage="after"):
    return SurveyRow(
        radiograph_number="R-001",
        stage=stage,
        performed_at=WHEN,
        performed_by="0142",
        instrument="Ludlum 3",
        calibration_due=DUE,
    )


def point_row(label="boundary", metres=140, surface=False):
    return SurveyPointRow(
        label=label,
        distance=Length.from_m(metres),
        bearing_degrees=0,
        reading=usv_per_hour(20),
        at_device_surface=surface,
    )


def test_every_safety_table_is_on_the_metadata():
    names = table_names()
    for expected in ("survey", "survey_point", "dose_entry", "dosimeter"):
        assert expected in names


def test_a_survey_holds_its_points():
    with session() as db:
        survey = survey_row()
        survey.points.append(point_row())
        survey.points.append(point_row("device", 1, surface=True))
        db.add(survey)
        db.commit()
        found = db.scalars(select(SurveyRow)).one()
        assert len(found.points) == 2
        assert found.points[0].reading == usv_per_hour(20)


def test_a_stage_the_schema_does_not_know_is_refused():
    with session() as db:
        db.add(survey_row(stage="during"))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_point_needs_a_distance():
    with session() as db:
        survey = survey_row()
        bad = point_row()
        bad.distance = Length.ZERO
        survey.points.append(bad)
        db.add(survey)
        with pytest.raises(IntegrityError):
            db.commit()


def test_the_surface_flag_is_a_column_and_not_inferred():
    with session() as db:
        survey = survey_row()
        survey.points.append(point_row("device", 140, surface=True))
        db.add(survey)
        db.commit()
        found = db.scalars(select(SurveyPointRow)).one()
        assert found.at_device_surface is True
        assert found.distance == Length.from_m(140)


def test_deleting_a_survey_takes_its_points():
    with session() as db:
        survey = survey_row()
        survey.points.append(point_row())
        db.add(survey)
        db.commit()
        db.delete(survey)
        db.commit()
        assert db.scalars(select(SurveyPointRow)).all() == []


def test_dose_is_an_append_only_ledger_rather_than_a_total():
    with session() as db:
        for taken in (usv(500), usv(300), usv(120)):
            db.add(
                DoseEntryRow(
                    badge="0142",
                    year=2024,
                    taken=taken,
                    recorded_at=WHEN,
                    source_of_entry="badge",
                )
            )
        db.commit()
        entries = db.scalars(select(DoseEntryRow)).all()
        assert len(entries) == 3
        total = sum((e.taken for e in entries), Dose.ZERO)
        assert total == usv(920)


def test_the_total_can_be_summed_in_the_database_too():
    with session() as db:
        for taken in (usv(500), usv(300)):
            db.add(
                DoseEntryRow(
                    badge="0142",
                    year=2024,
                    taken=taken,
                    recorded_at=WHEN,
                    source_of_entry="badge",
                )
            )
        db.commit()
        summed = db.scalar(select(func.sum(DoseEntryRow.taken)).where(DoseEntryRow.badge == "0142"))
        assert summed == usv(800)


def test_a_negative_dose_entry_is_refused():
    with session() as db:
        entry = DoseEntryRow(
            badge="0142", year=2024, taken=usv(1), recorded_at=WHEN, source_of_entry="badge"
        )
        db.add(entry)
        db.commit()
        with pytest.raises(IntegrityError):
            db.execute(
                text("UPDATE dose_entry SET taken = -1 WHERE id = :row"),
                {"row": entry.id},
            )
            db.commit()


def test_a_dosimeter_kind_the_schema_does_not_know_is_refused():
    with session() as db:
        db.add(
            DosimeterRow(
                serial="X-1",
                kind="lucky charm",
                badge="0142",
                worker_class="adult worker",
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_two_dosimeters_cannot_share_a_serial():
    with session() as db:
        for _ in range(2):
            db.add(
                DosimeterRow(
                    serial="A-77",
                    kind="alarming ratemeter",
                    badge="0142",
                    worker_class="adult worker",
                    calibration_due=date(2025, 1, 1),
                )
            )
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_badge_needs_no_calibration_date_in_the_schema():
    with session() as db:
        db.add(
            DosimeterRow(serial="B-4471", kind="badge", badge="0142", worker_class="adult worker")
        )
        db.commit()
        assert db.scalars(select(DosimeterRow)).one().calibration_due is None
