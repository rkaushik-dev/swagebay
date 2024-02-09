from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.length import Length, inch, mm, thou


def test_an_inch_is_exactly_twenty_five_thousand_four_hundred_micrometres():
    assert inch(1).um == 25400
    assert inch("0.375").um == 9525
    assert thou(1).um == 25


def test_the_round_trip_through_both_systems_is_lossless():
    wall = inch("0.375")
    assert wall.inch == Decimal("0.375")
    assert wall.mm == Decimal("9.525")
    assert Length.from_mm(wall.mm) == wall


def test_millimetre_values_stay_whole():
    assert mm("9.53").um == 9530
    assert mm(100).m == Decimal("0.1")
    assert Length.from_cm(61).um == 610000
    assert Length.from_m("0.61").um == 610000


def test_lengths_sort_the_way_the_wire_gauge_card_does():
    wires = [mm("0.25"), mm("0.16"), mm("0.40"), mm("0.20"), mm("0.32")]
    assert [w.mm for w in sorted(wires)] == [
        Decimal("0.16"),
        Decimal("0.20"),
        Decimal("0.25"),
        Decimal("0.32"),
        Decimal("0.40"),
    ]


def test_addition_and_subtraction_stay_exact():
    assert inch("0.375") + inch("0.125") == inch("0.5")
    assert mm(10) - mm("9.53") == Length(470)
    assert -mm(3) == mm(-3)
    assert abs(mm(-3)) == mm(3)


def test_scaling_rounds_half_away_from_zero():
    assert (Length(101) * Decimal("0.5")).um == 51
    assert (Length(-101) * Decimal("0.5")).um == -51
    assert (mm(1) * 2).um == 2000
    assert (2 * mm(1)).um == 2000
    assert (mm(1) / 3).um == 333


def test_dividing_by_zero_is_refused():
    with pytest.raises(ValidationError):
        mm(1) / 0


def test_a_ratio_is_dimensionless_and_exact():
    assert inch(24).ratio(inch(12)) == Decimal(2)
    with pytest.raises(ValidationError):
        mm(1).ratio(Length.ZERO)


def test_parse_reads_the_units_a_procedure_is_written_in():
    assert Length.parse("9.53 mm").um == 9530
    assert Length.parse("0.375 in").um == 9525
    assert Length.parse('24"').um == 609600
    assert Length.parse("  610MM ").um == 610000
    assert Length.parse("40 thou").um == 1016
    assert Length.parse("40 mil").um == 1016
    assert Length.parse("250 um").um == 250
    assert Length.parse("1.5 m").um == 1500000


def test_parse_refuses_anything_it_cannot_read():
    for bad in ["", "mm", "9.53", "9.53 kg", "nine mm", "9,53 mm"]:
        with pytest.raises(ValidationError):
            Length.parse(bad)


def test_micrometres_must_be_whole():
    with pytest.raises(ValidationError):
        Length(9.5)  # type: ignore[arg-type]
    with pytest.raises(ValidationError):
        Length(True)  # type: ignore[arg-type]


def test_presentation_is_readable_on_a_technique_sheet():
    assert str(inch("0.375")) == "9.53 mm"
    assert inch("0.375").format_mm(3) == "9.525 mm"
    assert mm("9.525").format_inch() == "0.375 in"
    assert repr(mm(1)) == "Length(1000)"


def test_a_length_will_not_combine_with_a_bare_number():
    with pytest.raises(TypeError):
        mm(1) + 1  # type: ignore[operator]
    with pytest.raises(TypeError):
        mm(1) - 1  # type: ignore[operator]


def test_zero_is_shared_and_falsy_in_the_useful_way():
    assert Length.ZERO.um == 0
    assert Length(0) == Length.ZERO
    assert mm(1) - mm(1) == Length.ZERO
