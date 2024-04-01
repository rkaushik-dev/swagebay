from datetime import date

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.personnel import Level, LicenceRole, Person
from qu_imager.procedure import Code, ProcedureRegister, Revision


def signer(level=Level.LEVEL_III, certified=date(2022, 1, 1)):
    return Person(
        badge="0001",
        name="level three",
        level=level,
        licence_role=LicenceRole.SAFETY_OFFICER,
        certified_on=certified,
        near_vision_on=date(2024, 1, 1),
        colour_perception_on=date(2024, 1, 1),
    )


def revision(rev=0, effective=date(2023, 1, 1), approved=date(2022, 12, 15), by=None):
    return Revision(
        number="RT-001",
        revision=rev,
        title="radiography of butt welds",
        code=Code.ASME_V,
        effective_on=effective,
        approved_by=by if by is not None else signer(),
        approved_on=approved,
    )


def test_a_revision_labels_itself_the_way_the_cover_sheet_does():
    assert revision(2).label == "RT-001 rev 2"
    assert str(revision(2)) == "RT-001 rev 2 (ASME Section V)"


def test_an_approval_needs_both_a_signature_and_a_date():
    with pytest.raises(ValidationError):
        Revision(
            number="RT-001",
            revision=0,
            title="t",
            code=Code.ASME_V,
            effective_on=date(2023, 1, 1),
            approved_by=signer(),
        )
    with pytest.raises(ValidationError):
        Revision(
            number="RT-001",
            revision=0,
            title="t",
            code=Code.ASME_V,
            effective_on=date(2023, 1, 1),
            approved_on=date(2022, 12, 1),
        )


def test_a_draft_with_no_approval_at_all_is_a_legal_object():
    draft = Revision(
        number="RT-002",
        revision=0,
        title="draft",
        code=Code.ASME_V,
        effective_on=date(2023, 1, 1),
    )
    assert not draft.is_approved
    assert not draft.approval_is_sound()


def test_a_revision_cannot_take_effect_before_it_was_signed():
    with pytest.raises(ValidationError):
        revision(effective=date(2022, 1, 1), approved=date(2022, 12, 15))


def test_only_a_level_three_signature_is_sound():
    assert revision().approval_is_sound()
    assert not revision(by=signer(level=Level.LEVEL_II)).approval_is_sound()


def test_a_signature_from_somebody_whose_certification_had_lapsed_is_not_sound():
    lapsed = signer(certified=date(2018, 1, 1))
    assert not revision(by=lapsed).approval_is_sound()


def test_the_register_answers_as_of_a_date_and_not_as_of_today():
    register = ProcedureRegister(
        [
            revision(0, effective=date(2023, 1, 1), approved=date(2022, 12, 15)),
            revision(1, effective=date(2024, 3, 1), approved=date(2024, 2, 20)),
        ]
    )
    assert register.effective_on("RT-001", date(2023, 6, 1)).revision == 0
    assert register.effective_on("RT-001", date(2024, 6, 1)).revision == 1
    assert register.effective_on("RT-001", date(2024, 3, 1)).revision == 1


def test_work_before_the_first_revision_has_no_governing_procedure():
    register = ProcedureRegister([revision(0, effective=date(2023, 1, 1))])
    with pytest.raises(ConfigurationError) as caught:
        register.effective_on("RT-001", date(2022, 6, 1))
    assert caught.value.context["day"] == "2022-06-01"


def test_an_unapproved_revision_is_never_returned_as_effective():
    draft = Revision(
        number="RT-001",
        revision=2,
        title="draft",
        code=Code.ASME_V,
        effective_on=date(2024, 1, 1),
    )
    register = ProcedureRegister([revision(0, effective=date(2023, 1, 1)), draft])
    assert register.effective_on("RT-001", date(2024, 6, 1)).revision == 0


def test_an_old_revision_stays_resolvable_forever():
    old = revision(0, effective=date(2023, 1, 1))
    new = revision(1, effective=date(2024, 3, 1), approved=date(2024, 2, 20))
    register = ProcedureRegister([old, new])
    assert register.effective_on("RT-001", date(2023, 6, 1)) == old
    assert register.is_superseded(old, date(2024, 6, 1))
    assert not register.is_superseded(old, date(2023, 6, 1))


def test_the_history_of_a_procedure_comes_back_in_order():
    register = ProcedureRegister(
        [
            revision(1, effective=date(2024, 3, 1), approved=date(2024, 2, 20)),
            revision(0, effective=date(2023, 1, 1)),
        ]
    )
    assert [r.revision for r in register.history("RT-001")] == [0, 1]
    assert register.history("RT-999") == ()


def test_the_same_revision_twice_is_refused():
    with pytest.raises(ValidationError):
        ProcedureRegister([revision(0), revision(0)])


def test_adding_a_revision_keeps_the_register_sound():
    register = ProcedureRegister([revision(0, effective=date(2023, 1, 1))])
    register.add(revision(1, effective=date(2024, 3, 1), approved=date(2024, 2, 20)))
    assert register.current("RT-001", date(2024, 6, 1)).revision == 1
    with pytest.raises(ValidationError):
        register.add(revision(1, effective=date(2024, 3, 1), approved=date(2024, 2, 20)))


def test_the_register_lists_the_procedures_it_holds():
    other = Revision(
        number="RT-002",
        revision=0,
        title="other",
        code=Code.API_1104,
        effective_on=date(2023, 1, 1),
        approved_by=signer(),
        approved_on=date(2022, 12, 1),
    )
    register = ProcedureRegister([revision(0), other])
    assert register.numbers() == ("RT-001", "RT-002")
    assert str(Code.API_1104) == "API 1104"
