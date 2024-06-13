from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.dose import DoseRate, mr_per_hour
from qu_imager.quantities.length import Length, mm
from qu_imager.safety.field import (
    BoundaryKind,
    RadiationField,
    rope_off,
)
from qu_imager.safety.shielding import Barrier
from qu_imager.sources.device import collimator
from qu_imager.sources.isotope import CO60, IR192

DOWN = Decimal(0)
SIDE = Decimal(180)


def bare(load=74):
    return RadiationField(IR192, curie(load))


def collimated(load=74, cup="w60"):
    return RadiationField(IR192, curie(load), collimator=collimator(cup))


def test_the_boundaries_come_out_where_field_experience_says_they_do():
    boundaries = bare().boundaries(DOWN)
    assert Length.from_m(130) < boundaries[BoundaryKind.RESTRICTED] < Length.from_m(135)
    assert Length.from_m(18) < boundaries[BoundaryKind.HIGH_RADIATION] < Length.from_m(20)


def test_a_hotter_boundary_sits_closer_in():
    boundaries = bare().boundaries(DOWN)
    assert (
        boundaries[BoundaryKind.HIGH_RADIATION]
        < boundaries[BoundaryKind.RADIATION_AREA]
        < boundaries[BoundaryKind.RESTRICTED]
    )


def test_a_collimator_does_nothing_down_the_beam():
    assert collimated().boundary(BoundaryKind.RESTRICTED, DOWN) == bare().boundary(
        BoundaryKind.RESTRICTED, DOWN
    )


def test_a_collimator_pulls_the_side_boundary_right_in():
    cup = collimated()
    assert (
        cup.boundary(BoundaryKind.RESTRICTED, SIDE)
        < cup.boundary(BoundaryKind.RESTRICTED, DOWN) / 3
    )


def test_the_rope_off_is_two_distances_when_a_collimator_is_fitted():
    plan = rope_off(collimated())
    assert plan.is_directional
    assert plan.down_beam > plan.off_beam
    assert "down the beam" in plan.describe()


def test_the_rope_off_is_one_distance_without_one():
    plan = rope_off(bare())
    assert not plan.is_directional
    assert plan.describe().startswith("restricted area at ")


def test_a_barrier_pulls_the_boundary_in_the_same_way_in_every_direction():
    shielded = RadiationField(IR192, curie(74), barriers=(Barrier("lead", mm(10)),))
    assert shielded.boundary(BoundaryKind.RESTRICTED, DOWN) < bare().boundary(
        BoundaryKind.RESTRICTED, DOWN
    )
    assert shielded.boundary(BoundaryKind.RESTRICTED, DOWN) == shielded.boundary(
        BoundaryKind.RESTRICTED, SIDE
    )


def test_barriers_stack():
    one = RadiationField(IR192, curie(74), barriers=(Barrier("lead", mm(10)),))
    two = RadiationField(
        IR192, curie(74), barriers=(Barrier("lead", mm(10)), Barrier("lead", mm(10)))
    )
    assert two.attenuation(DOWN) < one.attenuation(DOWN)
    assert two.boundary(BoundaryKind.RESTRICTED, DOWN) < one.boundary(BoundaryKind.RESTRICTED, DOWN)


def test_a_collimator_and_a_barrier_both_apply():
    both = RadiationField(
        IR192,
        curie(74),
        collimator=collimator("w60"),
        barriers=(Barrier("lead", mm(10)),),
    )
    assert both.attenuation(SIDE) < both.attenuation(DOWN)
    assert both.attenuation(DOWN) < 1


def test_nothing_in_the_way_attenuates_nothing():
    assert bare().attenuation(DOWN) == Decimal(1)
    assert bare().attenuation(SIDE) == Decimal(1)


def test_a_hotter_source_pushes_every_boundary_out():
    small = bare(20).boundaries(DOWN)
    big = bare(120).boundaries(DOWN)
    for kind in BoundaryKind:
        assert big[kind] > small[kind]


def test_cobalt_pushes_them_further_still_curie_for_curie():
    iridium = RadiationField(IR192, curie(40))
    cobalt = RadiationField(CO60, curie(40))
    assert cobalt.boundary(BoundaryKind.RESTRICTED, DOWN) > iridium.boundary(
        BoundaryKind.RESTRICTED, DOWN
    )


def test_standing_inside_a_boundary_is_reported_as_such():
    plan = bare()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, DOWN)
    assert plan.is_inside(BoundaryKind.RESTRICTED, Length(boundary.um // 2), DOWN)
    assert not plan.is_inside(BoundaryKind.RESTRICTED, Length(boundary.um * 2), DOWN)


def test_the_boundary_is_rounded_away_from_the_source():
    plan = bare()
    boundary = plan.boundary(BoundaryKind.RESTRICTED, DOWN)
    assert plan.rate_at(boundary, DOWN) <= BoundaryKind.RESTRICTED.rate


def test_a_rate_needs_a_distance_and_a_boundary_needs_a_rate():
    with pytest.raises(ValidationError):
        bare().rate_at(Length.ZERO, DOWN)
    with pytest.raises(ValidationError):
        bare().distance_to(DoseRate.ZERO, DOWN)


def test_an_empty_pig_has_no_boundary_and_nothing_to_rope_off():
    empty = RadiationField(IR192, Activity.ZERO)
    assert empty.boundary(BoundaryKind.RESTRICTED, DOWN) == Length.ZERO
    with pytest.raises(TechniqueError):
        rope_off(empty)


def test_the_posting_text_is_what_goes_on_the_sign():
    assert BoundaryKind.HIGH_RADIATION.sign_reads == "danger high radiation area"
    assert BoundaryKind.RESTRICTED.rate == mr_per_hour(2)
    assert str(BoundaryKind.RADIATION_AREA) == "radiation area"


def test_a_boundary_can_be_projected_from_a_meter_reading():
    plan = bare()
    reading = plan.rate_at(Length.from_m(10), DOWN)
    projected = plan.distance_from_reading(reading, Length.from_m(10), mr_per_hour(2))
    from_table = plan.boundary(BoundaryKind.RESTRICTED, DOWN)
    assert abs(projected.um - from_table.um) < 100_000


def test_a_reading_carries_whatever_scatter_the_site_produced():
    plan = bare()
    hot = mr_per_hour(plan.rate_at(Length.from_m(10), DOWN).mr_per_hour * 2)
    projected = plan.distance_from_reading(hot, Length.from_m(10), mr_per_hour(2))
    assert projected > plan.boundary(BoundaryKind.RESTRICTED, DOWN)


def test_projecting_a_reading_rounds_away_from_the_source():
    plan = bare()
    projected = plan.distance_from_reading(mr_per_hour(200), Length.from_m(1), mr_per_hour(2))
    assert projected == Length.from_m(10)


def test_projecting_needs_a_real_distance_and_a_real_target():
    plan = bare()
    with pytest.raises(ValidationError):
        plan.distance_from_reading(mr_per_hour(200), Length.ZERO, mr_per_hour(2))
    with pytest.raises(ValidationError):
        plan.distance_from_reading(mr_per_hour(200), Length.from_m(1), DoseRate.ZERO)


def test_a_reading_of_nothing_projects_to_no_boundary():
    plan = bare()
    assert plan.distance_from_reading(DoseRate.ZERO, Length.from_m(1), mr_per_hour(2)) == (
        Length.ZERO
    )
