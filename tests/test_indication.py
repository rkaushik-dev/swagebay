from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.identity import ClockPosition
from qu_imager.quantities.length import Length, mm


def slag(at=100, length=20, width=1):
    return Indication(Kind.ESI, mm(at), mm(length), mm(width))


def pore(at=100, width=2):
    return Indication(Kind.P, mm(at), Length.ZERO, mm(width))


def test_an_elongated_indication_is_judged_on_its_length():
    found = slag(length=30)
    assert found.size == mm(30)
    assert found.ends_at == mm(130)


def test_a_rounded_indication_is_judged_on_its_width():
    found = pore(width=3)
    assert found.size == mm(3)
    assert found.length == Length.ZERO
    assert found.ends_at == found.starts_at


def test_an_elongated_indication_without_a_length_is_refused():
    with pytest.raises(ValidationError) as caught:
        Indication(Kind.ESI, mm(100), Length.ZERO, mm(1))
    assert caught.value.context["kind"] == "ESI"


def test_a_rounded_indication_without_a_width_is_refused():
    with pytest.raises(ValidationError):
        Indication(Kind.P, mm(100), mm(3), Length.ZERO)


def test_negative_sizes_and_positions_are_refused():
    with pytest.raises(ValidationError):
        Indication(Kind.ESI, Length(-1), mm(10), mm(1))
    with pytest.raises(ValidationError):
        Indication(Kind.ESI, mm(100), Length(-1), mm(1))


def test_a_depth_fraction_sits_inside_the_unit_interval():
    Indication(Kind.U, mm(100), mm(10), depth_fraction=Decimal("0.125"))
    with pytest.raises(ValidationError):
        Indication(Kind.U, mm(100), mm(10), depth_fraction=Decimal(0))
    with pytest.raises(ValidationError):
        Indication(Kind.U, mm(100), mm(10), depth_fraction=Decimal("1.5"))


def test_overlap_and_gap_are_measured_along_the_weld():
    first = slag(at=100, length=20)
    second = slag(at=130, length=20)
    assert not first.overlaps(second)
    assert first.gap_to(second) == mm(10)
    assert second.gap_to(first) == mm(10)


def test_touching_indications_do_not_overlap_and_have_no_gap():
    first = slag(at=100, length=20)
    second = slag(at=120, length=20)
    assert not first.overlaps(second)
    assert first.gap_to(second) == Length.ZERO


def test_overlapping_indications_report_no_gap():
    first = slag(at=100, length=40)
    second = slag(at=120, length=20)
    assert first.overlaps(second)
    assert first.gap_to(second) == Length.ZERO


def test_a_set_sorts_itself_along_the_weld():
    found = IndicationSet(mm(1000), (slag(at=300), slag(at=100), slag(at=200)))
    assert [i.starts_at.mm for i in found.indications] == [100, 200, 300]
    assert len(found) == 3


def test_an_indication_off_the_end_of_the_weld_is_refused():
    with pytest.raises(ValidationError) as caught:
        IndicationSet(mm(100), (slag(at=90, length=20),))
    assert caught.value.context["ends_at_mm"] == "110"


def test_a_weld_with_no_length_is_refused():
    with pytest.raises(ValidationError):
        IndicationSet(Length.ZERO, ())


def test_a_set_reports_what_kinds_it_holds():
    found = IndicationSet(mm(1000), (slag(), pore(at=400), Indication(Kind.C, mm(600), mm(5))))
    assert found.kinds() == frozenset({Kind.ESI, Kind.P, Kind.C})
    assert len(found.of_kind(Kind.ESI)) == 1
    assert found.of_kind(Kind.U) == ()


def test_the_planar_ones_can_be_pulled_out_on_their_own():
    found = IndicationSet(mm(1000), (slag(), pore(at=400), Indication(Kind.C, mm(600), mm(5))))
    assert [i.kind for i in found.planar()] == [Kind.C]


def test_lengths_of_one_kind_are_totalled():
    found = IndicationSet(
        mm(1000), (slag(at=100, length=20), slag(at=300, length=30), pore(at=500))
    )
    assert found.total_length_of(Kind.ESI) == mm(50)
    assert found.total_length_of(Kind.P) == Length.ZERO


def test_a_window_picks_up_everything_that_overlaps_it():
    found = IndicationSet(mm(1000), (slag(at=100, length=20), slag(at=290, length=30)))
    assert len(found.within(mm(0), mm(200))) == 1
    assert len(found.within(mm(0), mm(300))) == 2
    assert len(found.within(mm(400), mm(900))) == 0


def test_a_window_with_no_width_is_refused():
    found = IndicationSet(mm(1000), (slag(),))
    with pytest.raises(ValidationError):
        found.within(mm(100), mm(100))


def test_the_longest_of_a_kind_is_found_on_size_not_length():
    found = IndicationSet(mm(1000), (pore(at=100, width=2), pore(at=200, width=5)))
    assert found.longest_of(Kind.P).size == mm(5)
    assert found.longest_of(Kind.C) is None


def test_an_indication_prints_the_way_a_report_line_reads():
    found = Indication(Kind.ESI, mm(100), mm(25), mm(1), clock=ClockPosition.parse("3:00"))
    assert found.describe() == "ESI 25.0 mm from 100 mm at 3:00"
    assert slag().describe() == "ESI 20.0 mm from 100 mm"
