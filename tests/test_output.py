from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.dose import DoseRate, mr_per_hour, usv_per_hour
from qu_imager.quantities.length import Length, mm
from qu_imager.sources.isotope import CO60, IR192, SE75
from qu_imager.sources.output import (
    ONE_METRE,
    distance_for_rate,
    distance_from_measured,
    dose_rate_at,
    output_at_one_metre,
    scale_to_distance,
)


def test_a_hundred_curies_of_iridium_reads_forty_eight_roentgen_at_a_metre():
    rate = output_at_one_metre(IR192, curie(100))
    assert rate.mr_per_hour == Decimal(48000)
    assert rate.msv_per_hour == Decimal(480)


def test_cobalt_is_nearly_three_times_hotter_than_iridium_curie_for_curie():
    ir = output_at_one_metre(IR192, curie(10))
    co = output_at_one_metre(CO60, curie(10))
    assert co.nsv_per_hour > ir.nsv_per_hour * 2
    assert output_at_one_metre(SE75, curie(10)) < ir


def test_an_empty_pig_reads_nothing():
    assert output_at_one_metre(IR192, Activity.ZERO) == DoseRate.ZERO


def test_the_rate_at_a_metre_matches_the_general_form():
    assert dose_rate_at(IR192, curie(100), ONE_METRE) == output_at_one_metre(IR192, curie(100))


def test_halving_the_distance_quadruples_the_rate():
    near = dose_rate_at(IR192, curie(100), Length.from_m(2))
    far = dose_rate_at(IR192, curie(100), Length.from_m(4))
    assert abs(near.nsv_per_hour - far.nsv_per_hour * 4) < 10


def test_backing_off_by_ten_cuts_the_rate_by_a_hundred():
    close = dose_rate_at(IR192, curie(90), Length.from_m(1))
    away = dose_rate_at(IR192, curie(90), Length.from_m(10))
    assert abs(close.nsv_per_hour - away.nsv_per_hour * 100) < 100


def test_a_rate_needs_somewhere_to_be_measured():
    with pytest.raises(ValidationError):
        dose_rate_at(IR192, curie(90), Length.ZERO)
    with pytest.raises(ValidationError):
        dose_rate_at(IR192, curie(90), Length(-1))


def test_a_measured_reading_moves_by_the_same_square_law():
    measured = mr_per_hour(200)
    moved = scale_to_distance(measured, Length.from_m(2), Length.from_m(4))
    assert abs(moved.mr_per_hour - Decimal(50)) < Decimal("0.01")
    back = scale_to_distance(measured, Length.from_m(4), Length.from_m(2))
    assert abs(back.mr_per_hour - Decimal(800)) < Decimal("0.01")


def test_scaling_needs_two_real_distances():
    with pytest.raises(ValidationError):
        scale_to_distance(mr_per_hour(200), Length.ZERO, Length.from_m(4))
    with pytest.raises(ValidationError):
        scale_to_distance(mr_per_hour(200), Length.from_m(4), Length.ZERO)


def test_the_distance_a_rate_falls_to_a_target():
    boundary = distance_for_rate(IR192, curie(100), mr_per_hour(2))
    assert Length.from_m(154) < boundary < Length.from_m(156)


def test_the_boundary_distance_is_rounded_away_from_the_source():
    exact = distance_for_rate(IR192, curie(100), output_at_one_metre(IR192, curie(100)))
    assert exact >= ONE_METRE
    assert dose_rate_at(IR192, curie(100), exact) <= output_at_one_metre(IR192, curie(100))


def test_a_hotter_source_pushes_the_boundary_out():
    small = distance_for_rate(IR192, curie(20), mr_per_hour(2))
    big = distance_for_rate(IR192, curie(100), mr_per_hour(2))
    assert big > small


def test_a_target_rate_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        distance_for_rate(IR192, curie(100), DoseRate.ZERO)
    with pytest.raises(ValidationError):
        distance_from_measured(mr_per_hour(200), ONE_METRE, DoseRate.ZERO)


def test_an_empty_source_needs_no_boundary():
    assert distance_for_rate(IR192, Activity.ZERO, mr_per_hour(2)) == Length.ZERO


def test_the_boundary_can_be_worked_out_from_a_meter_reading_instead():
    from_table = distance_for_rate(IR192, curie(100), usv_per_hour(20))
    reading = dose_rate_at(IR192, curie(100), Length.from_m(5))
    from_meter = distance_from_measured(reading, Length.from_m(5), usv_per_hour(20))
    assert abs(from_table.um - from_meter.um) < 20000


def test_a_reading_taken_at_no_distance_cannot_be_projected():
    with pytest.raises(ValidationError):
        distance_from_measured(mr_per_hour(200), Length.ZERO, mr_per_hour(2))
    assert distance_from_measured(mr_per_hour(200), mm(500), mr_per_hour(2)) == mm(5000)
