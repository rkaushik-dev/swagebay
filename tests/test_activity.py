from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import BQ_PER_CURIE, Activity, curie, gbq


def test_a_curie_is_exactly_thirty_seven_gigabecquerels():
    assert curie(1).bq == BQ_PER_CURIE
    assert curie(1).bq == 37_000_000_000
    assert curie(1).gbq == Decimal(37)


def test_a_working_projector_load_reads_the_same_both_ways():
    source = curie(90)
    assert source.curie == Decimal(90)
    assert source.tbq == Decimal("3.33")
    assert Activity.from_tbq("3.33") == source


def test_small_amounts_survive_the_round_trip():
    assert Activity.from_millicurie(10).bq == 370_000_000
    assert Activity.from_millicurie(10).curie == Decimal("0.01")
    assert gbq(1).bq == 1_000_000_000


def test_activities_sort_by_strength():
    loads = [curie(90), curie("13.5"), curie(120), curie(41)]
    assert [a.curie for a in sorted(loads)] == [
        Decimal("13.5"),
        Decimal(41),
        Decimal(90),
        Decimal(120),
    ]


def test_a_negative_activity_is_refused_at_construction():
    with pytest.raises(ValidationError):
        Activity(-1)
    with pytest.raises(ValidationError):
        curie(1) * -1


def test_subtraction_will_not_run_a_source_below_zero():
    assert curie(90) - curie(40) == curie(50)
    with pytest.raises(ValidationError):
        curie(40) - curie(90)


def test_the_ratio_of_two_activities_is_the_decay_fraction():
    assert curie(45).ratio(curie(90)) == Decimal("0.5")
    with pytest.raises(ValidationError):
        curie(1).ratio(Activity.ZERO)


def test_spent_is_measured_against_a_floor_the_office_sets():
    floor = curie(20)
    assert curie(15).is_spent(floor)
    assert not curie(20).is_spent(floor)
    assert not curie(90).is_spent(floor)


def test_parse_reads_both_the_licence_and_the_crew():
    assert Activity.parse("90 Ci").bq == 90 * BQ_PER_CURIE
    assert Activity.parse("3.33 TBq").bq == 3_330_000_000_000
    assert Activity.parse("  100 mCi ").curie == Decimal("0.1")
    assert Activity.parse("500 uCi").curie == Decimal("0.0005")
    assert Activity.parse("740 GBq").gbq == Decimal(740)
    assert Activity.parse("1 bq").bq == 1


def test_parse_refuses_a_unit_it_does_not_know():
    for bad in ["", "90", "Ci", "90 sieverts", "90 mm", "ninety Ci"]:
        with pytest.raises(ValidationError):
            Activity.parse(bad)


def test_becquerels_must_be_whole():
    with pytest.raises(ValidationError):
        Activity(1.5)  # type: ignore[arg-type]
    with pytest.raises(ValidationError):
        Activity(True)  # type: ignore[arg-type]


def test_activity_will_not_combine_with_a_bare_number():
    with pytest.raises(TypeError):
        curie(1) + 1  # type: ignore[operator]
    with pytest.raises(TypeError):
        curie(1) - 1  # type: ignore[operator]


def test_presentation_is_what_goes_on_the_paperwork():
    assert str(curie(90)) == "90.0 Ci"
    assert curie("13.47").format_curie(2) == "13.47 Ci"
    assert curie(90).format_tbq() == "3.33 TBq"
    assert repr(Activity(37)) == "Activity(37)"
