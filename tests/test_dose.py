from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.dose import Dose, DoseRate, mr_per_hour, usv, usv_per_hour
from qu_imager.quantities.duration import Duration, minutes, seconds


def test_the_field_convention_is_ten_microsieverts_to_the_milliroentgen():
    assert mr_per_hour(1).usv_per_hour == Decimal(10)
    assert mr_per_hour("0.2").usv_per_hour == Decimal(2)
    assert usv_per_hour(20).mr_per_hour == Decimal(2)


def test_a_dose_reads_the_same_in_both_systems():
    taken = usv(500)
    assert taken.msv == Decimal("0.5")
    assert taken.mrem == Decimal(50)
    assert Dose.from_mrem(50) == taken
    assert Dose.from_msv("0.5") == taken


def test_doses_and_rates_both_sort():
    assert sorted([usv(30), usv(5), usv(12)]) == [usv(5), usv(12), usv(30)]
    assert sorted([usv_per_hour(30), usv_per_hour(5)]) == [usv_per_hour(5), usv_per_hour(30)]


def test_neither_quantity_can_be_negative():
    with pytest.raises(ValidationError):
        Dose(-1)
    with pytest.raises(ValidationError):
        DoseRate(-1)
    with pytest.raises(ValidationError):
        usv(1) - usv(2)
    with pytest.raises(ValidationError):
        usv(1) * -1
    with pytest.raises(ValidationError):
        usv_per_hour(1) * -1


def test_a_rate_over_a_span_is_a_dose():
    assert usv_per_hour(20).over(Duration.from_hours(1)) == usv(20)
    assert usv_per_hour(20).over(minutes(30)) == usv(10)
    assert usv_per_hour(20).over(minutes(6)) == usv(2)
    assert usv_per_hour(0).over(minutes(30)) == Dose.ZERO


def test_stay_time_truncates_so_the_budget_is_never_overspent():
    assert usv_per_hour(20).stay_time(usv(10)) == minutes(30)
    assert usv_per_hour(3).stay_time(usv(1)) == seconds(1200)
    generous = usv_per_hour(7).stay_time(usv(1))
    assert generous.ms == 514285
    with pytest.raises(ValidationError):
        DoseRate.ZERO.stay_time(usv(10))


def test_a_dose_taken_as_a_fraction_of_the_limit_is_what_the_office_tracks():
    quarterly = Dose.from_msv(5)
    assert usv(500).fraction_of(quarterly) == Decimal("0.1")
    with pytest.raises(ValidationError):
        usv(1).fraction_of(Dose.ZERO)


def test_parse_reads_a_dose_written_any_of_the_usual_ways():
    assert Dose.parse("500 uSv") == usv(500)
    assert Dose.parse("0.5 mSv") == usv(500)
    assert Dose.parse("50 mrem") == usv(500)
    assert Dose.parse("1 Sv").msv == Decimal(1000)
    assert Dose.parse("5 rem").msv == Decimal(50)
    assert Dose.parse("250 nsv").nsv == 250


def test_parse_reads_a_rate_off_a_survey_sheet():
    assert DoseRate.parse("2 mR/h") == usv_per_hour(20)
    assert DoseRate.parse("2 mR/hr") == usv_per_hour(20)
    assert DoseRate.parse("20 uSv/h") == usv_per_hour(20)
    assert DoseRate.parse("1 R / h").msv_per_hour == Decimal(10)
    assert DoseRate.parse("0.5 mSv/h").usv_per_hour == Decimal(500)


def test_parse_refuses_a_dose_where_a_rate_belongs():
    with pytest.raises(ValidationError):
        DoseRate.parse("500 uSv")
    with pytest.raises(ValidationError):
        Dose.parse("20 uSv/h")
    for bad in ["", "hot", "2 mR", "2 mR/day"]:
        with pytest.raises(ValidationError):
            DoseRate.parse(bad)


def test_the_two_types_do_not_mix():
    with pytest.raises(TypeError):
        usv(1) + usv_per_hour(1)  # type: ignore[operator]
    with pytest.raises(TypeError):
        usv_per_hour(1) + usv(1)  # type: ignore[operator]


def test_presentation_matches_the_survey_sheet():
    assert str(usv(500)) == "500.0 uSv"
    assert usv(500).format_mrem() == "50.0 mrem"
    assert str(usv_per_hour(20)) == "20.0 uSv/h"
    assert usv_per_hour(20).format_mr_per_hour() == "2.00 mR/h"
    assert repr(Dose(1)) == "Dose(1)"
    assert repr(DoseRate(1)) == "DoseRate(1)"
