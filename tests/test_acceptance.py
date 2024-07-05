import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code, Verdict, codes_disagree, judge
from qu_imager.evaluation.assessment import Disposition
from qu_imager.evaluation.indication import Indication, IndicationSet
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.quantities.length import Length, mm

WALL = mm(10)


def weld(*indications, length=1000):
    return IndicationSet(mm(length), indications)


def lof(at=0, length=10):
    return Indication(Kind.IF, mm(at), mm(length))


def test_a_clean_weld_is_accepted_under_both_codes():
    clean = weld()
    assert judge(Code.API_1104, clean, WALL).accepted
    assert judge(Code.ASME_VIII, clean, WALL).accepted
    assert not codes_disagree(clean, WALL)


def test_the_verdict_carries_the_code_it_was_reached_under():
    verdict = judge(Code.API_1104, weld(), WALL)
    assert verdict.code is Code.API_1104
    assert verdict.describe() == "accept to API 1104"
    assert isinstance(verdict, Verdict)


def test_the_two_codes_disagree_about_a_ten_millimetre_lack_of_fusion():
    found = weld(lof(length=10))
    assert judge(Code.API_1104, found, WALL).accepted
    assert not judge(Code.ASME_VIII, found, WALL).accepted
    assert codes_disagree(found, WALL)


def test_the_asme_finding_quotes_an_asme_clause():
    verdict = judge(Code.ASME_VIII, weld(lof(length=10)), WALL)
    assert verdict.clauses() == ("UW-51(b2)",)
    assert verdict.describe().startswith("ASME Section VIII Division 1: ")


def test_the_api_finding_quotes_an_api_clause():
    verdict = judge(Code.API_1104, weld(lof(length=40)), WALL)
    assert verdict.clauses() == ("9.3.4",)


def test_a_crack_is_rejected_under_both():
    found = weld(Indication(Kind.C, mm(10), mm(20)))
    for code in Code:
        assert judge(code, found, WALL).disposition is Disposition.REJECT
    assert not codes_disagree(found, WALL)


def test_slag_is_judged_by_api_and_left_alone_by_the_asme_elongated_rule():
    slag = Indication(Kind.ESI, mm(10), mm(60), mm(1))
    found = weld(slag)
    assert not judge(Code.API_1104, found, WALL).accepted
    assert judge(Code.ASME_VIII, found, WALL).accepted


def test_the_codes_are_told_apart_by_the_shape_of_their_rules():
    assert Code.API_1104.judges_by_kind
    assert not Code.ASME_VIII.judges_by_kind
    assert Code.ASME_VIII.window_moves_with_the_wall
    assert not Code.API_1104.window_moves_with_the_wall


def test_a_wall_of_nothing_is_refused_under_either_code():
    for code in Code:
        with pytest.raises(ValidationError):
            judge(code, weld(), Length.ZERO)


def test_the_same_indications_flip_with_the_wall_under_asme_only():
    found = weld(lof(length=10))
    assert not judge(Code.ASME_VIII, found, mm(10)).accepted
    assert judge(Code.ASME_VIII, found, mm(40)).accepted
    assert judge(Code.API_1104, found, mm(10)).accepted
    assert judge(Code.API_1104, found, mm(40)).accepted


def test_the_code_prints_the_way_a_specification_names_it():
    assert str(Code.API_1104) == "API 1104"
    assert str(Code.ASME_VIII) == "ASME Section VIII Division 1"
