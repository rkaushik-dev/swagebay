from datetime import date

import pytest

from qu_imager.errors import ValidationError
from qu_imager.personnel import (
    Level,
    LicenceRole,
    Person,
    interpreters,
    supervision_satisfied,
)

TODAY = date(2024, 6, 1)


def person(
    badge="0142",
    level=Level.LEVEL_II,
    role=LicenceRole.RADIOGRAPHER,
    certified=date(2023, 1, 15),
    vision=date(2024, 1, 10),
    colour=date(2024, 1, 10),
):
    return Person(
        badge=badge,
        name="crew",
        level=level,
        licence_role=role,
        certified_on=certified,
        near_vision_on=vision,
        colour_perception_on=colour,
    )


def test_the_two_authorities_are_separate():
    assert Level.LEVEL_II.may_interpret
    assert not Level.LEVEL_I.may_interpret
    assert LicenceRole.RADIOGRAPHER.may_operate_unattended
    assert not LicenceRole.ASSISTANT.may_operate_unattended


def test_a_level_one_sets_up_and_does_not_interpret():
    hand = person(level=Level.LEVEL_I)
    assert not hand.may_interpret_on(TODAY)
    assert not hand.may_write_technique_on(TODAY)
    assert hand.may_shoot_alone_on(TODAY)


def test_only_a_level_three_approves_a_procedure():
    assert not person(level=Level.LEVEL_II).may_approve_procedure_on(TODAY)
    assert person(level=Level.LEVEL_III).may_approve_procedure_on(TODAY)


def test_a_new_level_two_without_a_licence_cannot_touch_the_source():
    fresh = person(level=Level.LEVEL_II, role=LicenceRole.NONE)
    assert fresh.may_interpret_on(TODAY)
    assert not fresh.may_shoot_alone_on(TODAY)


def test_a_lapsed_eye_exam_stops_interpretation_and_not_the_shooting():
    tired = person(vision=date(2023, 1, 10))
    assert "near vision test" in tired.lapsed_on(TODAY)
    assert not tired.may_interpret_on(TODAY)
    assert tired.may_shoot_alone_on(TODAY)


def test_a_lapsed_certification_stops_both():
    old = person(certified=date(2020, 1, 15))
    assert "certification" in old.lapsed_on(TODAY)
    assert not old.may_interpret_on(TODAY)
    assert not old.may_shoot_alone_on(TODAY)


def test_every_lapsed_credential_is_reported_not_just_the_first():
    stale = person(certified=date(2019, 1, 1), vision=date(2019, 1, 1), colour=date(2019, 1, 1))
    assert stale.lapsed_on(TODAY) == (
        "certification",
        "near vision test",
        "colour perception test",
    )


def test_the_certification_runs_three_years_to_the_day():
    hand = person(certified=date(2021, 6, 1))
    assert hand.certification_expires == date(2024, 5, 31)
    assert not hand.is_current_on(date(2024, 5, 31))
    assert hand.is_current_on(date(2024, 5, 30))


def test_the_vision_tests_run_a_year():
    hand = person(vision=date(2023, 6, 1), colour=date(2023, 6, 1))
    assert hand.near_vision_expires == date(2024, 5, 31)
    assert hand.colour_perception_expires == date(2024, 5, 31)
    assert not hand.is_current_on(TODAY)


def test_the_colour_test_can_be_the_one_that_bites():
    hand = person(colour=date(2023, 1, 1))
    assert hand.lapsed_on(TODAY) == ("colour perception test",)


def test_an_assistant_alone_on_site_is_not_supervised():
    assistant = person(badge="0201", level=Level.LEVEL_I, role=LicenceRole.ASSISTANT)
    assert not supervision_satisfied((assistant,), TODAY)
    assert supervision_satisfied((assistant, person()), TODAY)


def test_an_empty_crew_is_not_supervised_either():
    assert not supervision_satisfied((), TODAY)


def test_a_crew_reports_who_can_read_the_film():
    reader = person(badge="0142")
    hand = person(badge="0201", level=Level.LEVEL_I)
    blind = person(badge="0303", vision=date(2022, 1, 1))
    assert [p.badge for p in interpreters((reader, hand, blind), TODAY)] == ["0142"]


def test_a_person_needs_a_badge_and_the_radiography_method():
    with pytest.raises(ValidationError):
        person(badge="  ")
    with pytest.raises(ValidationError):
        Person(
            badge="1",
            name="x",
            level=Level.LEVEL_II,
            licence_role=LicenceRole.RADIOGRAPHER,
            certified_on=TODAY,
            near_vision_on=TODAY,
            colour_perception_on=TODAY,
            methods=frozenset({"UT"}),
        )


def test_a_person_prints_with_the_level_on_the_card():
    assert str(person()) == "crew (0142, Level II)"
    assert str(LicenceRole.ASSISTANT) == "radiographer assistant"
