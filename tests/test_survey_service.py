from datetime import datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.dose import mr_per_hour
from qu_imager.quantities.length import Length
from qu_imager.safety.survey import SurveyStage
from qu_imager.services.surveys import (
    Point,
    missing_surveys,
    record_survey,
    require_clear,
    scatter_factor,
    stuck_source_reports,
    surveys_for,
)

from conftest import database

WHEN = datetime(2024, 12, 4, 23, 30)
DUE = datetime(2025, 6, 1)


def point(label="boundary", metres=140, mr=2, surface=False):
    return Point(
        label=label,
        distance=Length.from_m(metres),
        bearing_degrees=0,
        reading=mr_per_hour(mr),
        at_device_surface=surface,
    )


def record(work, number="R-001", stage=SurveyStage.AFTER, points=None, due=DUE, at=WHEN):
    return record_survey(
        work,
        number,
        stage,
        at,
        "0142",
        "Ludlum 3",
        due,
        points or (point(), point("device", 1, mr=1, surface=True)),
    )


def test_a_sound_after_survey_is_clear():
    made = database()
    with transaction(made) as work:
        outcome = record(work)
        assert outcome.is_clear
        assert outcome.hottest_label == "boundary"


def test_the_survey_and_its_points_are_stored():
    made = database()
    with transaction(made) as work:
        record(work)
        stored = surveys_for(work, "R-001")
        assert len(stored) == 1
        assert len(stored[0].points) == 2
        assert stored[0].performed_by == "0142"


def test_an_after_survey_with_no_surface_reading_is_a_fault():
    made = database()
    with transaction(made) as work:
        outcome = record(work, points=(point(),))
        assert "no reading was taken at the device surface" in outcome.faults


def test_a_hot_device_surface_is_recorded_and_then_reported_loudly():
    made = database()
    with transaction(made) as work:
        outcome = record(work, points=(point(), point("device", 1, mr=900, surface=True)))
        assert not outcome.is_clear
        assert "not fully retracted" in outcome.faults[0]
        assert stuck_source_reports(work) == ("R-001: 900.00 mR/h at the device",)


def test_a_failing_survey_is_still_written_down():
    made = database()
    with transaction(made) as work:
        record(work, points=(point("device", 1, mr=900, surface=True),))
        assert len(surveys_for(work, "R-001")) == 1


def test_an_expired_meter_calibration_is_a_fault():
    made = database()
    with transaction(made) as work:
        outcome = record(work, due=WHEN - timedelta(days=1))
        assert "the survey meter calibration has expired" in outcome.faults


def test_a_before_survey_needs_no_surface_reading():
    made = database()
    with transaction(made) as work:
        outcome = record(work, stage=SurveyStage.BEFORE, points=(point(),))
        assert outcome.is_clear


def test_a_survey_with_nothing_in_it_or_nobody_on_it_is_refused():
    made = database()
    with transaction(made) as work:
        with pytest.raises(ValidationError):
            record_survey(work, "R-001", SurveyStage.AFTER, WHEN, "0142", "Ludlum 3", DUE, ())
        with pytest.raises(ValidationError):
            record_survey(work, "R-001", SurveyStage.AFTER, WHEN, "  ", "Ludlum 3", DUE, (point(),))


def test_a_point_needs_a_label_and_a_place():
    with pytest.raises(ValidationError):
        Point("  ", Length.from_m(1), 0, mr_per_hour(2))
    with pytest.raises(ValidationError):
        Point("a", Length.ZERO, 0, mr_per_hour(2))


def test_an_exposure_with_neither_survey_is_short_of_both():
    made = database()
    with transaction(made) as work:
        assert missing_surveys(work, ("R-001",)) == (
            "R-001 has no before survey",
            "R-001 has no after survey",
        )


def test_an_exposure_with_only_the_before_survey_is_short_of_the_after():
    made = database()
    with transaction(made) as work:
        record(work, stage=SurveyStage.BEFORE, points=(point(),))
        assert missing_surveys(work, ("R-001",)) == ("R-001 has no after survey",)


def test_an_exposure_with_both_is_short_of_nothing():
    made = database()
    with transaction(made) as work:
        record(work, stage=SurveyStage.BEFORE, points=(point(),))
        record(work, stage=SurveyStage.AFTER)
        assert missing_surveys(work, ("R-001",)) == ()


def test_the_shortfall_is_reported_per_exposure():
    made = database()
    with transaction(made) as work:
        record(work, "R-001", SurveyStage.BEFORE, points=(point(),))
        record(work, "R-001", SurveyStage.AFTER)
        record(work, "R-002", SurveyStage.BEFORE, points=(point(),))
        assert missing_surveys(work, ("R-001", "R-002")) == ("R-002 has no after survey",)


def test_the_surveys_on_one_film_come_back_in_time_order():
    made = database()
    with transaction(made) as work:
        record(work, stage=SurveyStage.AFTER, at=WHEN + timedelta(minutes=30))
        record(work, stage=SurveyStage.BEFORE, points=(point(),), at=WHEN)
        assert [s.stage for s in surveys_for(work, "R-001")] == ["before", "after"]


def test_the_scatter_factor_is_the_ratio_the_office_trends():
    assert scatter_factor(mr_per_hour(4), mr_per_hour(2)) == Decimal(2)
    with pytest.raises(ValidationError):
        scatter_factor(mr_per_hour(4), mr_per_hour(0))


def test_a_caller_that_cannot_proceed_can_insist_on_a_clear_survey():
    made = database()
    with transaction(made) as work:
        require_clear(record(work))
        with pytest.raises(ConflictError):
            require_clear(record(work, "R-002", points=(point(),)))
