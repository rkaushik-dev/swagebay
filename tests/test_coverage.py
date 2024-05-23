from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.coverage import (
    DEFAULT_PATH_ALLOWANCE,
    incidence_angle,
    path_factor,
    plan_coverage,
    standoff_for_exposures,
    usable_half_arc,
)
from qu_imager.materials import STEEL
from qu_imager.quantities.length import Length, mm

LOOSE = Decimal("1.6")


def line(nominal="12", schedule="40"):
    return ps.girth_weld(nominal, schedule, STEEL)


def test_the_beam_is_square_on_at_the_middle_of_the_film():
    radius = line().od / 2
    assert incidence_angle(radius, mm(600), 0) == 0.0
    assert path_factor(radius, mm(600), 0) == Decimal(1)


def test_the_angle_opens_up_round_the_pipe():
    radius = line().od / 2
    angles = [incidence_angle(radius, mm(600), phi) for phi in (0, 10, 20, 30, 45)]
    assert angles == sorted(angles)
    assert 12 < angles[1] < 13


def test_the_path_gets_longer_with_the_angle():
    radius = line().od / 2
    factors = [path_factor(radius, mm(600), phi) for phi in (0, 10, 20, 30)]
    assert factors == sorted(factors)
    assert factors[2] > Decimal("1.10")


def test_a_source_further_back_bends_the_ray_less():
    radius = line().od / 2
    near = incidence_angle(radius, mm(200), 20)
    far = incidence_angle(radius, mm(2000), 20)
    assert far < near


def test_a_grazing_ray_is_refused_rather_than_returning_a_huge_factor():
    radius = line().od / 2
    with pytest.raises(TechniqueError):
        path_factor(radius, mm(600), 120)


def test_the_geometry_needs_a_pipe_and_a_standoff():
    with pytest.raises(ValidationError):
        incidence_angle(Length.ZERO, mm(600), 10)
    with pytest.raises(ValidationError):
        incidence_angle(mm(100), Length.ZERO, 10)


def test_the_usable_arc_is_where_the_path_stays_inside_the_allowance():
    joint = line()
    half = usable_half_arc(joint, mm(600))
    radius = joint.od / 2
    assert path_factor(radius, mm(600), float(half)) <= DEFAULT_PATH_ALLOWANCE
    assert path_factor(radius, mm(600), float(half) + 1) > DEFAULT_PATH_ALLOWANCE


def test_a_looser_allowance_buys_a_wider_arc():
    joint = line()
    assert usable_half_arc(joint, mm(600), LOOSE) > usable_half_arc(joint, mm(600))


def test_backing_the_source_off_buys_a_wider_arc_too():
    joint = line()
    assert usable_half_arc(joint, mm(600), LOOSE) > usable_half_arc(joint, mm(200), LOOSE)


def test_an_allowance_of_one_or_less_is_refused():
    with pytest.raises(ValidationError):
        usable_half_arc(line(), mm(600), Decimal(1))
    with pytest.raises(ValidationError):
        usable_half_arc(line(), mm(600), Decimal("0.9"))


def test_the_code_minimum_is_a_floor_and_geometry_usually_asks_for_more():
    plan = plan_coverage(line(), mm(600), 3)
    assert plan.exposures == 10
    assert plan.exposures > 3


def test_the_planned_exposures_really_do_overlap():
    plan = plan_coverage(line(), mm(600), 3)
    assert plan.has_overlap
    assert plan.usable_half_arc_degrees * 2 > plan.separation_degrees
    assert plan.overlap_degrees > 0


def test_a_looser_allowance_costs_fewer_exposures():
    strict = plan_coverage(line(), mm(600), 3)
    loose = plan_coverage(line(), mm(600), 3, LOOSE)
    assert loose.exposures < strict.exposures
    assert loose.exposures == 5


def test_the_code_minimum_wins_when_the_geometry_is_generous():
    plan = plan_coverage(line(), mm(3000), 3, Decimal("2.0"))
    assert plan.exposures == 4
    assert plan.separation_degrees == Decimal(90)


def test_a_plan_needs_a_positive_code_minimum():
    with pytest.raises(ValidationError):
        plan_coverage(line(), mm(600), 0)


def test_working_backwards_to_the_standoff_a_shot_count_needs():
    joint = line()
    standoff = standoff_for_exposures(joint, 4, LOOSE)
    assert usable_half_arc(joint, standoff, LOOSE) >= Decimal(45)
    assert plan_coverage(joint, standoff, 4, LOOSE).exposures == 4


def test_fewer_exposures_need_the_source_further_back():
    joint = line()
    assert standoff_for_exposures(joint, 4, LOOSE) > standoff_for_exposures(joint, 6, LOOSE)


def test_three_exposures_are_unreachable_under_the_office_allowance():
    with pytest.raises(TechniqueError) as caught:
        standoff_for_exposures(line(), 3)
    assert caught.value.context["exposures"] == 3


def test_a_standoff_needs_a_positive_exposure_count():
    with pytest.raises(ValidationError):
        standoff_for_exposures(line(), 0)


def test_the_plan_prints_the_way_the_sheet_reads():
    plan = plan_coverage(line(), mm(600), 3, LOOSE)
    assert plan.describe() == "5 exposures 72.0 degrees apart, each carrying 83.5 degrees"
