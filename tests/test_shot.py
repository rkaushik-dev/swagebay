from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.geometry.shot import (
    ShotType,
    choose_shot,
    coverage_arc,
    exposures_required,
    feasible_shots,
    penetrated_thickness,
)
from qu_imager.materials import STEEL
from qu_imager.quantities.length import mm

HEAD = mm(25)
CLEARANCE = mm(5)


def big_line(reinforcement="0"):
    return ps.girth_weld("12", "40", STEEL, reinforcement=mm(reinforcement))


def small_line(reinforcement="0"):
    return ps.girth_weld("1", "80", STEEL, reinforcement=mm(reinforcement))


def test_a_single_wall_shot_crosses_one_wall_and_one_cap():
    joint = big_line(reinforcement="2.5")
    assert penetrated_thickness(joint, ShotType.PANORAMIC) == mm("12.81")
    assert penetrated_thickness(joint, ShotType.SWSI) == mm("12.81")


def test_a_double_wall_shot_crosses_two_walls_and_two_caps():
    joint = big_line(reinforcement="2.5")
    assert penetrated_thickness(joint, ShotType.DWSI) == mm("25.62")
    assert penetrated_thickness(joint, ShotType.DWDI_ELLIPTICAL) == mm("25.62")


def test_the_cap_is_the_part_people_forget():
    bare = penetrated_thickness(big_line(), ShotType.DWSI)
    capped = penetrated_thickness(big_line(reinforcement="2.5"), ShotType.DWSI)
    assert capped - bare == mm(5)


def test_which_shots_read_one_wall_and_which_read_two():
    assert ShotType.DWSI.walls_penetrated == 2
    assert ShotType.DWSI.walls_interpreted == 1
    assert ShotType.DWDI_ELLIPTICAL.walls_interpreted == 2
    assert ShotType.PANORAMIC.walls_penetrated == 1


def test_the_code_minimum_exposure_counts():
    assert ShotType.PANORAMIC.minimum_exposures == 1
    assert ShotType.DWDI_ELLIPTICAL.minimum_exposures == 2
    assert ShotType.DWDI_SUPERIMPOSED.minimum_exposures == 3
    assert ShotType.DWSI.minimum_exposures == 3


def test_the_separations_match_the_counts():
    assert ShotType.DWDI_ELLIPTICAL.exposure_separation_degrees == 90
    assert ShotType.DWDI_SUPERIMPOSED.exposure_separation_degrees == 60
    assert ShotType.DWSI.exposure_separation_degrees == 120


def test_a_plate_weld_takes_one_exposure_whatever_the_shot_type_says():
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    assert exposures_required(plate, ShotType.DWSI) == 1
    assert exposures_required(big_line(), ShotType.DWSI) == 3


def test_a_big_line_with_access_gets_the_panoramic_shot():
    assert choose_shot(big_line(), HEAD, CLEARANCE) is ShotType.PANORAMIC


def test_a_small_bore_line_gets_the_elliptical_shot():
    assert choose_shot(small_line(), HEAD, CLEARANCE) is ShotType.DWDI_ELLIPTICAL


def test_a_big_line_with_no_access_falls_back_to_double_wall_single_image():
    joint = big_line()
    assert choose_shot(joint, mm(400), CLEARANCE) is ShotType.DWSI


def test_double_wall_double_image_is_refused_above_three_and_a_half_inches():
    results = {f.shot: f for f in feasible_shots(big_line(), HEAD, CLEARANCE)}
    elliptical = results[ShotType.DWDI_ELLIPTICAL]
    assert not elliptical.available
    assert "89 mm" in elliptical.reason


def test_the_reason_against_a_shot_is_reported_in_words():
    results = {f.shot: f for f in feasible_shots(small_line(), mm(400), CLEARANCE)}
    assert results[ShotType.PANORAMIC].reason == "no room for the source inside the bore"
    assert results[ShotType.SWSI].reason == "no room for the source inside the bore"
    assert results[ShotType.DWDI_ELLIPTICAL].available


def test_plate_has_no_second_wall_to_shoot_through():
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    results = {f.shot: f for f in feasible_shots(plate, HEAD, CLEARANCE)}
    assert results[ShotType.DWSI].reason == "plate has no second wall"
    assert results[ShotType.DWDI_ELLIPTICAL].reason == "plate has no second wall"
    assert results[ShotType.CONTACT].available
    assert choose_shot(plate, HEAD, CLEARANCE) is ShotType.CONTACT


def test_a_seam_weld_cannot_be_shot_panoramically():
    seam = Joint(
        form=JointForm.PIPE_SEAM,
        wall=mm(10),
        material=STEEL,
        outside_diameter=mm(600),
        seam_length=mm(2000),
    )
    results = {f.shot: f for f in feasible_shots(seam, HEAD, CLEARANCE)}
    assert results[ShotType.PANORAMIC].reason == "a panoramic shot needs a circumferential weld"


def test_the_arc_each_exposure_carries():
    assert coverage_arc(big_line(), 3) == Decimal(120)
    assert coverage_arc(big_line(), 4) == Decimal(90)
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    assert coverage_arc(plate, 1) == Decimal(0)
    with pytest.raises(TechniqueError):
        coverage_arc(big_line(), 0)
