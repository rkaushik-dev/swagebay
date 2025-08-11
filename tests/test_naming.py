import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry.shot import ShotType
from qu_imager.imaging.selection import IndicatorSide, TechniqueClass
from qu_imager.naming import (
    code_named,
    kind_named,
    named,
    optional_shot_named,
    shot_named,
    side_named,
    technique_class_named,
)


def test_a_published_value_resolves():
    assert code_named("API 1104") is Code.API_1104
    assert shot_named("double wall single image") is ShotType.DWSI
    assert side_named("film side") is IndicatorSide.FILM


def test_the_member_name_resolves_too_and_it_does_so_everywhere():
    assert code_named("API_1104") is Code.API_1104
    assert shot_named("dwsi") is ShotType.DWSI
    assert side_named("FILM") is IndicatorSide.FILM
    assert technique_class_named("b") is TechniqueClass.B


def test_surrounding_space_is_ignored():
    assert code_named("  API 1104  ") is Code.API_1104
    assert shot_named(" panoramic ") is ShotType.PANORAMIC


def test_a_name_nobody_publishes_is_refused_with_the_list():
    with pytest.raises(ValidationError) as caught:
        code_named("BS 2600")
    assert caught.value.context["given"] == "BS 2600"
    assert "API 1104" in caught.value.context["known"]


def test_every_lookup_refuses_rather_than_defaulting():
    for call in (code_named, shot_named, side_named, technique_class_named, kind_named):
        with pytest.raises(ValidationError):
            call("nonsense")


def test_nothing_means_let_the_planner_pick():
    assert optional_shot_named(None) is None
    assert optional_shot_named("panoramic") is ShotType.PANORAMIC
    with pytest.raises(ValidationError):
        optional_shot_named("sideways")


def test_a_discontinuity_is_named_by_its_code_and_the_case_does_not_matter():
    assert kind_named("ESI") is Kind.ESI
    assert kind_named("esi") is Kind.ESI
    assert kind_named(" c ") is Kind.C


def test_a_discontinuity_is_also_named_by_its_description_now():
    """It was code only, because it had its own lookup. It has not any more.

    Accepting the description is what every other enum here already did, and
    keeping one of them stricter than the rest was an accident of having four
    copies rather than a decision anybody made.
    """
    assert kind_named("crack") is Kind.C
    assert kind_named("elongated slag inclusion") is Kind.ESI


def test_a_description_nobody_publishes_is_still_refused():
    with pytest.raises(ValidationError) as caught:
        kind_named("a nasty looking one")
    assert "crack" in caught.value.context["known"]


def test_the_general_lookup_names_what_it_was_looking_for():
    with pytest.raises(ValidationError) as caught:
        named(Code, "nothing", "acceptance code")
    assert "acceptance code" in caught.value.message


def test_every_member_of_every_enum_round_trips_through_its_value():
    for kind, call in (
        (Code, code_named),
        (ShotType, shot_named),
        (IndicatorSide, side_named),
        (TechniqueClass, technique_class_named),
    ):
        for member in kind:
            assert call(str(member.value)) is member


def test_every_member_round_trips_through_its_name_too():
    for kind, call in (
        (Code, code_named),
        (ShotType, shot_named),
        (IndicatorSide, side_named),
        (TechniqueClass, technique_class_named),
    ):
        for member in kind:
            assert call(member.name) is member


def test_every_discontinuity_code_round_trips():
    for member in Kind:
        assert kind_named(member.name) is member


def test_an_underscored_member_name_is_accepted_because_that_is_what_gets_typed():
    from qu_imager.naming import disposition_named, item_named
    from qu_imager.nonconformance import Disposition
    from qu_imager.safety.maintenance import Item

    assert disposition_named("use_as_is") is Disposition.USE_AS_IS
    assert disposition_named("use as is") is Disposition.USE_AS_IS
    assert disposition_named("USE_AS_IS") is Disposition.USE_AS_IS
    assert item_named("drive_cable") is Item.DRIVE_CABLE
    assert item_named("drive cable") is Item.DRIVE_CABLE
    assert item_named("drive-cable") is Item.DRIVE_CABLE


def test_an_enum_with_a_label_publishes_the_label_and_not_the_tuple():
    from qu_imager.naming import item_named, published
    from qu_imager.safety.maintenance import Item

    assert published(Item.DRIVE_CABLE) == "drive cable"
    with pytest.raises(ValidationError) as caught:
        item_named("flux capacitor")
    known = caught.value.context["known"]
    assert "drive cable" in known
    assert "Severity" not in known


def test_a_disposition_nobody_publishes_is_refused_with_the_ones_that_exist():
    from qu_imager.naming import disposition_named

    with pytest.raises(ValidationError) as caught:
        disposition_named("have a think")
    assert "rework" in caught.value.context["known"]
