from datetime import timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, TechniqueError
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.geometry.shot import ShotType
from qu_imager.imaging.film import film
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.materials import STEEL
from qu_imager.quantities.activity import curie
from qu_imager.quantities.duration import minutes
from qu_imager.quantities.length import mm
from qu_imager.technique import TechniqueRequest, plan_technique, sheet_or_refuse

from conftest import SHOT_DAY


def request_for(joint, source, system, **overrides):
    base = {
        "joint": joint,
        "source": source,
        "system": system,
        "film": film("d7"),
        "shot_at": SHOT_DAY,
    }
    base.update(overrides)
    return TechniqueRequest(**base)


def outside(joint, source, system, **overrides):
    return request_for(joint, source, system, shot=ShotType.DWSI, **overrides)


def test_a_line_with_access_is_planned_panoramic_and_takes_one_exposure(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(request_for(six_inch_weld, iridium_source, on_d7))
    assert sheet.shot is ShotType.PANORAMIC
    assert sheet.exposures == 1
    assert sheet.penetrated == mm("12.97")


def test_a_panoramic_source_sits_on_the_axis_so_the_distance_is_the_radius(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(request_for(six_inch_weld, iridium_source, on_d7))
    assert sheet.source_to_film == six_inch_weld.od / 2
    assert sheet.geometry.object_to_film == six_inch_weld.wall


def test_ninety_curies_inside_a_six_inch_line_is_too_hot_to_time(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(request_for(six_inch_weld, iridium_source, on_d7))
    assert not sheet.is_clean
    assert "ten second floor" in sheet.warnings[0]


def test_the_same_weld_shot_from_outside_is_a_workable_sheet(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert sheet.is_clean
    assert sheet.exposures == 8
    assert sheet.exposure.timer == "0:37"


def test_the_outside_shot_crosses_two_walls_and_two_caps(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert sheet.penetrated == mm("25.94")
    assert sheet.penetrated == six_inch_weld.total_thickness * 2


def test_the_indicator_is_read_off_the_penetrated_thickness_not_the_wall(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert sheet.indicator.indicator.identity == 30
    assert sheet.indicator.thickness == sheet.penetrated


def test_the_geometry_that_produced_the_distance_travels_with_the_sheet(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert sheet.geometry.source_to_film == sheet.source_to_film
    assert sheet.geometry.meets(six_inch_weld.wall)
    assert sheet.source_to_film > mm(1000)


def test_a_film_side_indicator_is_asked_for_and_marked(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(
        outside(six_inch_weld, iridium_source, on_d7, indicator_side=IndicatorSide.FILM)
    )
    assert sheet.indicator.needs_f_marker
    assert "F marker" in sheet.indicator.describe()


def test_a_slower_film_makes_a_longer_exposure(six_inch_weld, iridium_source, on_d7):
    fast = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    slow = plan_technique(outside(six_inch_weld, iridium_source, on_d7, film=film("d4")))
    assert slow.exposure.exposure > fast.exposure.exposure


def test_the_source_is_decayed_to_the_day_of_the_shot(six_inch_weld, iridium_source, on_d7):
    early = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    late = plan_technique(
        outside(six_inch_weld, iridium_source, on_d7, shot_at=SHOT_DAY + timedelta(days=80))
    )
    assert late.exposure.activity < early.exposure.activity
    assert late.exposure.exposure > early.exposure.exposure


def test_the_total_source_out_is_the_shot_times_the_exposure_count(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert sheet.total_source_out.ms == sheet.exposure.exposure.ms * sheet.exposures
    assert sheet.total_source_out.as_timer() == "4:56"


def test_a_source_with_a_lapsed_leak_test_warns_rather_than_refusing(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(
        outside(six_inch_weld, iridium_source, on_d7, shot_at=SHOT_DAY + timedelta(days=300))
    )
    assert not sheet.is_clean
    assert any("leak test" in w for w in sheet.warnings)


def test_a_spent_source_warns_on_the_working_floor(six_inch_weld, iridium_source, on_d7):
    iridium_source.working_floor = curie(85)
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert any("working floor" in w for w in sheet.warnings)


def test_a_window_too_short_for_the_joint_is_warned_about(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7, window=minutes(2)))
    assert any("source out" in w for w in sheet.warnings)


def test_a_generous_window_produces_no_warning(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7, window=minutes(600)))
    assert sheet.is_clean


def test_a_wall_outside_the_isotope_practical_range_is_warned_about(iridium_source, on_d7):
    thin = Joint(form=JointForm.PLATE_BUTT, wall=mm(8), material=STEEL)
    sheet = plan_technique(request_for(thin, iridium_source, on_d7))
    assert any("practical range" in w for w in sheet.warnings)


def test_a_heavy_line_stays_inside_the_iridium_range_on_a_single_wall(
    twelve_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(request_for(twelve_inch_weld, iridium_source, on_d7))
    assert not any("practical range" in w for w in sheet.warnings)
    assert sheet.penetrated == mm("19.98")


def test_a_density_with_no_established_chart_is_refused_outright(
    six_inch_weld, iridium_source, on_d7
):
    with pytest.raises(ConfigurationError):
        plan_technique(outside(six_inch_weld, iridium_source, on_d7, density=Decimal("2.2")))


def test_a_thickness_off_the_end_of_the_chart_is_refused_outright(iridium_source, on_d7):
    heavy = Joint(form=JointForm.PLATE_BUTT, wall=mm(100), material=STEEL)
    with pytest.raises(TechniqueError) as caught:
        plan_technique(request_for(heavy, iridium_source, on_d7))
    assert caught.value.context["thickness_mm"] == "100"


def test_a_plate_weld_takes_one_exposure_and_a_contact_shot(iridium_source, on_d7):
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(20), material=STEEL)
    sheet = plan_technique(request_for(plate, iridium_source, on_d7))
    assert sheet.shot is ShotType.CONTACT
    assert sheet.exposures == 1
    assert sheet.penetrated == mm(20)


def test_the_sheet_prints_as_one_line_for_the_job_book(six_inch_weld, iridium_source, on_d7):
    sheet = sheet_or_refuse(outside(six_inch_weld, iridium_source, on_d7))
    text = sheet.describe()
    assert "girth weld" in text
    assert "double wall single image" in text
    assert "source side" in text


def test_screens_shorten_the_shot_and_appear_in_the_chain(six_inch_weld, iridium_source, on_d7):
    with_screens = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    without = plan_technique(outside(six_inch_weld, iridium_source, on_d7, screens_fitted=False))
    assert with_screens.exposure.exposure < without.exposure.exposure
    assert "screens" in with_screens.exposure.corrections.names()


def test_the_screen_pair_named_on_the_sheet_is_the_one_for_the_beam(
    six_inch_weld, iridium_source, on_d7
):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    screens = next(c for c in sheet.exposure.corrections.corrections if c.name == "screens")
    assert "0.13 mm front" in screens.note


def test_no_screens_is_recorded_rather_than_left_out(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7, screens_fitted=False))
    screens = next(c for c in sheet.exposure.corrections.corrections if c.name == "screens")
    assert screens.note == "no screens"
    assert screens.factor == Decimal(1)


def test_a_collimator_costs_a_little_exposure(six_inch_weld, iridium_source, on_d7):
    bare = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    cupped = plan_technique(outside(six_inch_weld, iridium_source, on_d7, collimator_key="w60"))
    assert cupped.exposure.exposure > bare.exposure.exposure
    assert "collimator" in cupped.exposure.corrections.names()


def test_a_tighter_cup_costs_less_than_a_wider_one(six_inch_weld, iridium_source, on_d7):
    tight = plan_technique(outside(six_inch_weld, iridium_source, on_d7, collimator_key="du45"))
    wide = plan_technique(outside(six_inch_weld, iridium_source, on_d7, collimator_key="w90"))
    assert tight.exposure.exposure <= wide.exposure.exposure


def test_no_collimator_leaves_the_chain_alone(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert "collimator" not in sheet.exposure.corrections.names()


def test_a_collimator_nothing_knows_about_is_refused(six_inch_weld, iridium_source, on_d7):
    from qu_imager.errors import ConfigurationError

    with pytest.raises(ConfigurationError):
        plan_technique(outside(six_inch_weld, iridium_source, on_d7, collimator_key="w99"))


def test_the_right_pair_for_the_beam_draws_no_warning(six_inch_weld, iridium_source, on_d7):
    sheet = plan_technique(outside(six_inch_weld, iridium_source, on_d7))
    assert not any("front lead" in w for w in sheet.warnings)
