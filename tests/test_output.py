from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.dose import DoseRate
from qu_imager.quantities.length import Length
from qu_imager.sources.isotope import CO60, IR192, SE75
from qu_imager.sources.output import ONE_METRE, dose_rate_at, output_at_one_metre


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
