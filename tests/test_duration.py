from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.duration import Duration, minutes, seconds


def test_the_three_constructors_agree():
    assert seconds(90) == minutes("1.5")
    assert Duration.from_hours(1) == minutes(60)
    assert seconds("0.25").ms == 250


def test_exposures_sort_by_length():
    shots = [seconds(45), minutes(2), seconds(90), minutes("0.5")]
    assert [s.seconds for s in sorted(shots)] == [
        Decimal(30),
        Decimal(45),
        Decimal(90),
        Decimal(120),
    ]


def test_arithmetic_stays_in_the_type():
    assert seconds(30) + seconds(45) == seconds(75)
    assert minutes(2) - seconds(30) == seconds(90)
    assert seconds(30) * 4 == minutes(2)
    assert 4 * seconds(30) == minutes(2)
    assert minutes(2) / 4 == seconds(30)


def test_a_duration_cannot_be_driven_negative():
    with pytest.raises(ValidationError):
        Duration(-1)
    with pytest.raises(ValidationError):
        seconds(30) - minutes(2)
    with pytest.raises(ValidationError):
        seconds(30) * -1
    with pytest.raises(ValidationError):
        seconds(30) / 0


def test_the_ratio_of_two_durations_is_the_exposure_factor():
    assert minutes(2).ratio(seconds(30)) == Decimal(4)
    with pytest.raises(ValidationError):
        seconds(1).ratio(Duration.ZERO)


def test_clock_notation_is_read_the_way_it_is_written():
    assert Duration.parse("1:40") == seconds(100)
    assert Duration.parse("0:30") == seconds(30)
    assert Duration.parse("1:02:30") == seconds(3750)
    assert Duration.parse("2:07.5") == seconds("127.5")


def test_compound_notation_is_read_too():
    assert Duration.parse("90s") == seconds(90)
    assert Duration.parse("1m 40s") == seconds(100)
    assert Duration.parse("2h") == minutes(120)
    assert Duration.parse("1h 30m") == minutes(90)
    assert Duration.parse(" 45 S ") == seconds(45)


def test_parse_refuses_nonsense_and_out_of_range_clocks():
    for bad in ["", "later", "1:70", "1:60:00", "90", "1:2:3:4"]:
        with pytest.raises(ValidationError):
            Duration.parse(bad)


def test_rounding_up_to_a_whole_second_never_shortens_an_exposure():
    assert seconds("30.001").round_up_to_second() == seconds(31)
    assert seconds(30).round_up_to_second() == seconds(30)
    assert Duration.ZERO.round_up_to_second() == Duration.ZERO


def test_the_timer_string_is_what_the_crew_dials_in():
    assert seconds(100).as_timer() == "1:40"
    assert seconds(9).as_timer() == "0:09"
    assert seconds("89.2").as_timer() == "1:30"
    assert minutes(75).as_timer() == "1:15:00"
    assert str(seconds(45)) == "0:45"


def test_milliseconds_must_be_whole():
    with pytest.raises(ValidationError):
        Duration(1.5)  # type: ignore[arg-type]
    with pytest.raises(ValidationError):
        Duration(True)  # type: ignore[arg-type]


def test_a_duration_will_not_combine_with_a_bare_number():
    with pytest.raises(TypeError):
        seconds(1) + 1  # type: ignore[operator]
    with pytest.raises(TypeError):
        seconds(1) - 1  # type: ignore[operator]
    assert repr(Duration(250)) == "Duration(250)"
