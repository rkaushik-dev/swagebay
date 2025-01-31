"""Properties of the quantity types, checked over generated values.

The example based tests pin the values the trade quotes. These pin the laws.
The distinction matters because the laws are what the rest of the package
assumes without saying so: that a parse round trips, that ordering agrees with
the stored integer, that scaling by one changes nothing.

Every one of these was written after a real defect. The rounding law came from
a length that read 9.52 mm on the sheet and 9.53 mm in the database, and the
parse law came from a procedure line with a capital MM in it.
"""

from decimal import Decimal

from hypothesis import assume, given
from hypothesis import strategies as st

from qu_imager.quantities.activity import Activity
from qu_imager.quantities.dose import Dose, DoseRate
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length

micrometres = st.integers(min_value=-10_000_000, max_value=10_000_000)
positive_micrometres = st.integers(min_value=1, max_value=10_000_000)
becquerels = st.integers(min_value=0, max_value=10**15)
milliseconds = st.integers(min_value=0, max_value=10**10)


@given(micrometres)
def test_a_length_keeps_the_integer_it_was_given(um):
    assert Length(um).um == um


@given(micrometres, micrometres)
def test_lengths_order_the_way_their_micrometres_do(left, right):
    assert (Length(left) < Length(right)) == (left < right)


@given(micrometres, micrometres)
def test_addition_is_commutative(left, right):
    assert Length(left) + Length(right) == Length(right) + Length(left)


@given(micrometres, micrometres, micrometres)
def test_addition_is_associative(a, b, c):
    first = (Length(a) + Length(b)) + Length(c)
    second = Length(a) + (Length(b) + Length(c))
    assert first == second


@given(micrometres, micrometres)
def test_subtraction_undoes_addition(left, right):
    assert (Length(left) + Length(right)) - Length(right) == Length(left)


@given(micrometres)
def test_scaling_by_one_changes_nothing(um):
    assert Length(um) * 1 == Length(um)
    assert Length(um) * Decimal(1) == Length(um)


@given(micrometres)
def test_negating_twice_returns_the_original(um):
    once = -Length(um)
    assert -once == Length(um)


@given(micrometres)
def test_the_absolute_value_is_never_negative(um):
    assert abs(Length(um)).um >= 0


@given(positive_micrometres)
def test_a_length_round_trips_through_millimetres(um):
    value = Length(um)
    assert Length.from_mm(value.mm) == value


@given(positive_micrometres)
def test_a_length_round_trips_through_its_own_millimetre_text(um):
    value = Length(um)
    assert Length.parse(f"{value.mm} mm") == value


@given(positive_micrometres)
def test_a_length_round_trips_through_inches(um):
    value = Length(um)
    assert Length.from_inch(value.inch) == value


@given(positive_micrometres)
def test_the_unit_in_a_parsed_length_is_case_insensitive(um):
    value = Length(um)
    assert Length.parse(f"{value.mm} MM") == value
    assert Length.parse(f"{value.mm} mm") == value


@given(positive_micrometres, positive_micrometres)
def test_a_ratio_is_the_inverse_of_the_other_way_round(left, right):
    forward = Length(left).ratio(Length(right))
    back = Length(right).ratio(Length(left))
    assert abs(forward * back - Decimal(1)) < Decimal("1e-20")


@given(becquerels)
def test_an_activity_round_trips_through_curies(bq):
    value = Activity(bq)
    assert Activity.from_curie(value.curie) == value


@given(becquerels, becquerels)
def test_an_activity_never_goes_negative_through_subtraction(left, right):
    high, low = max(left, right), min(left, right)
    assert (Activity(high) - Activity(low)).bq >= 0


@given(becquerels, becquerels)
def test_activities_order_the_way_their_becquerels_do(left, right):
    assert (Activity(left) < Activity(right)) == (left < right)


@given(milliseconds)
def test_a_duration_round_trips_through_seconds(ms):
    value = Duration(ms)
    assert Duration.from_seconds(value.seconds) == value


@given(milliseconds)
def test_rounding_up_to_a_second_never_shortens_a_duration(ms):
    value = Duration(ms)
    assert value.round_up_to_second() >= value


@given(milliseconds)
def test_rounding_up_lands_on_a_whole_second(ms):
    assert Duration(ms).round_up_to_second().ms % 1000 == 0


@given(milliseconds)
def test_a_duration_that_is_already_whole_is_left_alone(ms):
    whole = Duration(ms - (ms % 1000))
    assert whole.round_up_to_second() == whole


@given(st.integers(min_value=0, max_value=10**12))
def test_a_dose_round_trips_through_microsieverts(nsv):
    value = Dose(nsv)
    assert Dose.from_usv(value.usv) == value


@given(st.integers(min_value=1, max_value=10**12), st.integers(min_value=1, max_value=10**7))
def test_a_rate_over_a_span_and_back_agrees_with_itself(nsv_per_hour, ms):
    rate = DoseRate(nsv_per_hour)
    span = Duration(ms)
    taken = rate.over(span)
    assume(taken.nsv > 0)
    assert taken.nsv <= rate.nsv_per_hour * (ms / 3_600_000) + 1


@given(st.integers(min_value=1, max_value=10**9), st.integers(min_value=1, max_value=10**9))
def test_a_stay_time_never_overspends_the_budget(nsv_per_hour, budget_nsv):
    rate = DoseRate(nsv_per_hour)
    budget = Dose(budget_nsv)
    stay = rate.stay_time(budget)
    assert rate.over(stay) <= budget
