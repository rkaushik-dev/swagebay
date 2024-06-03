from datetime import datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import curie
from qu_imager.quantities.dose import mr_per_hour
from qu_imager.quantities.length import Length, mm
from qu_imager.safety.field import BoundaryKind, RadiationField
from qu_imager.safety.survey import (
    SURFACE_READING_CEILING,
    SurveyPoint,
    SurveyRecord,
    SurveyStage,
    boundary_confirmed,
    compare,
    faults,
)
from qu_imager.sources.isotope import IR192

WHEN = datetime(2024, 6, 3, 23, 30)
DUE = datetime(2024, 12, 1)


def field_of(load=74):
    return RadiationField(IR192, curie(load))


def point(label="boundary", metres=140, bearing=0, mr=2, surface=False):
    return SurveyPoint(
        label=label,
        distance=Length.from_m(metres),
        bearing_degrees=Decimal(bearing),
        reading=mr_per_hour(mr),
        at_device_surface=surface,
    )


def record(stage=SurveyStage.AFTER, points=None, due=DUE, at=WHEN):
    return SurveyRecord(
        stage=stage,
        performed_at=at,
        performed_by="0142",
        instrument="Ludlum 3",
        instrument_calibration_due=due,
        points=points or (point(), point("device", 0.3, mr=1, surface=True)),
    )


def test_a_sound_post_exposure_survey_has_no_faults():
    assert faults(record()) == ()


def test_a_post_exposure_survey_with_no_surface_reading_is_a_fault():
    lonely = record(points=(point(),))
    assert "no reading was taken at the device surface" in faults(lonely)


def test_a_hot_device_surface_means_the_source_did_not_go_home():
    stuck = record(points=(point(), point("device", 0.3, mr=900, surface=True)))
    found = faults(stuck)
    assert len(found) == 1
    assert "not fully retracted" in found[0]


def test_the_surface_ceiling_is_where_the_call_is_made():
    at_limit = record(points=(point("device", 0.3, mr=200, surface=True),))
    assert faults(at_limit) == ()
    over = record(points=(point("device", 0.3, mr=201, surface=True),))
    assert faults(over)
    assert mr_per_hour(200) == SURFACE_READING_CEILING


def test_a_pre_exposure_survey_needs_no_surface_reading():
    before = record(stage=SurveyStage.BEFORE, points=(point(),))
    assert faults(before) == ()
    assert str(SurveyStage.BEFORE) == "before the exposure"


def test_an_expired_meter_calibration_is_a_fault_on_its_own():
    stale = record(due=WHEN - timedelta(days=1))
    assert not stale.instrument_is_current
    assert "the survey meter calibration has expired" in faults(stale)


def test_the_hottest_point_is_reported_for_the_record():
    survey = record(points=(point("a", 140, mr=1), point("b", 100, mr=4), point("c", 200, mr=2)))
    assert survey.hottest().label == "b"


def test_a_reading_that_matches_the_model_is_no_discrepancy():
    plan = field_of()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, Decimal(0))
    calculated = plan.rate_at(boundary, Decimal(0))
    survey = record(
        points=(
            SurveyPoint("boundary", boundary, Decimal(0), calculated),
            point("device", 0.3, mr=1, surface=True),
        )
    )
    assert compare(survey, plan) == ()


def test_a_reading_much_hotter_than_the_model_is_the_finding():
    plan = field_of()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, Decimal(0))
    survey = record(
        points=(
            SurveyPoint("boundary", boundary, Decimal(0), mr_per_hour(20)),
            point("device", 0.3, mr=1, surface=True),
        )
    )
    found = compare(survey, plan)
    assert len(found) == 1
    assert found[0].measured_hotter
    assert "hotter" in found[0].describe()


def test_a_reading_much_colder_than_the_model_is_reported_the_other_way():
    plan = field_of()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, Decimal(0))
    survey = record(
        points=(
            SurveyPoint("boundary", boundary, Decimal(0), mr_per_hour("0.1")),
            point("device", 0.3, mr=1, surface=True),
        )
    )
    found = compare(survey, plan)
    assert len(found) == 1
    assert not found[0].measured_hotter
    assert "colder" in found[0].describe()


def test_scatter_inside_the_tolerance_is_not_a_discrepancy():
    plan = field_of()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, Decimal(0))
    calculated = plan.rate_at(boundary, Decimal(0))
    slightly_hot = mr_per_hour(calculated.mr_per_hour * Decimal("1.4"))
    survey = record(
        points=(
            SurveyPoint("boundary", boundary, Decimal(0), slightly_hot),
            point("device", 0.3, mr=1, surface=True),
        )
    )
    assert compare(survey, plan) == ()


def test_the_surface_reading_is_never_compared_against_the_model():
    plan = field_of()
    survey = record(points=(point("device", 0.3, mr=1, surface=True),))
    assert compare(survey, plan) == ()


def test_the_boundary_is_confirmed_when_every_reading_is_under_the_rate():
    survey = record(points=(point("a", 140, mr=2), point("device", 0.3, mr=900, surface=True)))
    assert boundary_confirmed(survey)
    hot = record(points=(point("a", 140, mr=3),))
    assert not boundary_confirmed(hot)


def test_a_survey_needs_readings_and_a_signature():
    with pytest.raises(ValidationError):
        SurveyRecord(
            stage=SurveyStage.AFTER,
            performed_at=WHEN,
            performed_by="0142",
            instrument="Ludlum 3",
            instrument_calibration_due=DUE,
            points=(),
        )
    with pytest.raises(ValidationError):
        SurveyRecord(
            stage=SurveyStage.AFTER,
            performed_at=WHEN,
            performed_by="   ",
            instrument="Ludlum 3",
            instrument_calibration_due=DUE,
            points=(point(),),
        )


def test_a_survey_point_needs_a_label_and_a_place():
    with pytest.raises(ValidationError):
        SurveyPoint("  ", mm(100), Decimal(0), mr_per_hour(2))
    with pytest.raises(ValidationError):
        SurveyPoint("a", Length.ZERO, Decimal(0), mr_per_hour(2))
