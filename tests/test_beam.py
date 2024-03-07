from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry.beam import (
    BeamGeometry,
    minimum_source_to_film,
    minimum_source_to_object,
    unsharpness_limit,
)
from qu_imager.quantities.length import Length, inch, mm


def test_the_three_distances_add_up():
    beam = BeamGeometry(source_to_object=mm(600), object_to_film=mm(10), focal_size=mm(3))
    assert beam.source_to_film == mm(610)


def test_building_from_the_tape_measurement_splits_it_correctly():
    beam = BeamGeometry.from_sfd(mm(610), mm(10), mm(3))
    assert beam.source_to_object == mm(600)
    assert beam.source_to_film == mm(610)


def test_a_geometry_where_the_object_does_not_fit_is_refused():
    with pytest.raises(ValidationError):
        BeamGeometry.from_sfd(mm(10), mm(10), mm(3))
    with pytest.raises(ValidationError):
        BeamGeometry.from_sfd(mm(5), mm(10), mm(3))


def test_unsharpness_is_the_pellet_scaled_by_the_distance_ratio():
    beam = BeamGeometry(source_to_object=mm(600), object_to_film=mm(10), focal_size=mm(3))
    assert beam.geometric_unsharpness == Length(50)


def test_moving_the_source_back_cuts_the_blur():
    near = BeamGeometry(mm(300), mm(10), mm(3))
    far = BeamGeometry(mm(900), mm(10), mm(3))
    assert far.geometric_unsharpness < near.geometric_unsharpness
    assert near.geometric_unsharpness == Length(100)


def test_film_hard_against_the_pipe_gives_no_blur_at_all():
    flat = BeamGeometry(mm(600), Length.ZERO, mm(3))
    assert flat.geometric_unsharpness == Length.ZERO
    assert flat.magnification == Decimal(1)


def test_magnification_is_the_ratio_of_the_two_far_distances():
    beam = BeamGeometry(mm(600), mm(600), mm(3))
    assert beam.magnification == Decimal(2)


def test_the_asme_limits_step_rather_than_curving():
    assert unsharpness_limit(inch("1.99")) == inch("0.020")
    assert unsharpness_limit(inch(2)) == inch("0.030")
    assert unsharpness_limit(inch("2.99")) == inch("0.030")
    assert unsharpness_limit(inch(3)) == inch("0.040")
    assert unsharpness_limit(inch(4)) == inch("0.070")
    assert unsharpness_limit(inch(10)) == inch("0.070")


def test_a_thickness_of_nothing_has_no_limit_to_look_up():
    with pytest.raises(ValidationError):
        unsharpness_limit(Length.ZERO)
    with pytest.raises(ValidationError):
        unsharpness_limit(Length(-1))


def test_a_shot_that_meets_the_limit_passes_the_check_silently():
    beam = BeamGeometry(mm(600), mm(10), mm(3))
    assert beam.meets(mm(10))
    beam.check(mm(10))


def test_a_shot_over_the_limit_names_all_three_numbers():
    beam = BeamGeometry(mm(100), mm(20), mm(3))
    assert not beam.meets(mm(10))
    with pytest.raises(TechniqueError) as caught:
        beam.check(mm(10))
    assert caught.value.context["limit_mm"] == "0.508"
    assert caught.value.context["sod_mm"] == "100"


def test_the_same_geometry_can_fail_on_thin_wall_and_pass_on_thick():
    beam = BeamGeometry(mm(400), mm(80), mm(3))
    assert not beam.meets(inch(1))
    assert beam.meets(inch(5))


def test_the_minimum_distance_is_rounded_up_not_down():
    closest = minimum_source_to_object(mm(3), mm(10), inch("0.020"))
    assert closest.um == 59056
    assert BeamGeometry(closest, mm(10), mm(3)).meets(mm(10))
    too_close = Length(closest.um - 500)
    assert not BeamGeometry(too_close, mm(10), mm(3)).meets(mm(10))


def test_a_zero_gap_puts_no_distance_requirement_on_the_shot():
    assert minimum_source_to_object(mm(3), Length.ZERO, inch("0.020")) == Length(1)


def test_a_limit_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        minimum_source_to_object(mm(3), mm(10), Length.ZERO)


def test_the_distance_box_on_the_sheet_is_the_sod_plus_the_gap():
    sfd = minimum_source_to_film(mm(3), mm(10), mm(10))
    assert sfd == minimum_source_to_object(mm(3), mm(10), inch("0.020")) + mm(10)
    assert BeamGeometry.from_sfd(sfd, mm(10), mm(3)).meets(mm(10))


def test_a_geometry_needs_a_real_source_and_a_real_distance():
    with pytest.raises(ValidationError):
        BeamGeometry(Length.ZERO, mm(10), mm(3))
    with pytest.raises(ValidationError):
        BeamGeometry(mm(600), Length(-1), mm(3))
    with pytest.raises(ValidationError):
        BeamGeometry(mm(600), mm(10), Length.ZERO)
