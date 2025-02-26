import pytest

from qu_imager.errors import QualificationError, ValidationError
from qu_imager.imaging.enhancement import (
    Applied,
    History,
    Operation,
    check,
    faults,
    is_admissible,
    viewing_only,
)


def applied(operation, setting="", by="0142"):
    return Applied(operation=operation, setting=setting, applied_by=by)


def history(*operations):
    made = History()
    for operation in operations:
        made = made.then(applied(operation))
    return made


def test_windowing_leaves_the_stored_data_alone():
    assert Operation.WINDOW.is_reversible
    assert Operation.CONTRAST_STRETCH.is_reversible
    assert Operation.ROTATE_90.is_reversible
    assert Operation.MIRROR.is_reversible


def test_sharpening_and_smoothing_throw_information_away():
    assert not Operation.SHARPEN.is_reversible
    assert not Operation.SMOOTH.is_reversible
    assert not Operation.NOISE_REDUCTION.is_reversible


def test_the_irreversible_ones_are_the_ones_that_need_qualifying():
    for operation in Operation:
        if operation.needs_qualification:
            assert not operation.is_reversible


def test_two_operations_are_never_allowed_at_all():
    forbidden = {op for op in Operation if op.is_forbidden}
    assert forbidden == {Operation.LOCAL_EDIT, Operation.LOSSY_COMPRESSION}


def test_an_unprocessed_image_says_so():
    assert History().describe() == "unprocessed"
    assert History().is_reversible


def test_the_history_keeps_the_order_things_were_done_in():
    made = history(Operation.WINDOW, Operation.CONTRAST_STRETCH)
    assert [item.operation for item in made.operations] == [
        Operation.WINDOW,
        Operation.CONTRAST_STRETCH,
    ]
    assert " then " in made.describe()


def test_a_history_is_immutable_so_a_record_cannot_be_edited():
    first = history(Operation.WINDOW)
    second = first.then(applied(Operation.MIRROR))
    assert len(first.operations) == 1
    assert len(second.operations) == 2


def test_viewing_operations_leave_the_image_reversible():
    assert history(Operation.WINDOW, Operation.MIRROR).is_reversible


def test_one_sharpen_makes_the_whole_history_irreversible():
    made = history(Operation.WINDOW, Operation.SHARPEN)
    assert not made.is_reversible
    assert [item.operation for item in made.irreversible()] == [Operation.SHARPEN]


def test_a_viewing_only_history_passes():
    assert is_admissible(history(Operation.WINDOW, Operation.MIRROR), viewing_only())


def test_an_unqualified_sharpen_is_a_fault():
    found = faults(history(Operation.SHARPEN), viewing_only())
    assert len(found) == 1
    assert "not part of the qualified technique" in found[0]


def test_a_qualified_sharpen_is_allowed():
    qualified = viewing_only() | {Operation.SHARPEN}
    assert is_admissible(history(Operation.WINDOW, Operation.SHARPEN), qualified)


def test_qualifying_a_sharpen_does_not_qualify_a_smooth():
    qualified = viewing_only() | {Operation.SHARPEN}
    assert not is_admissible(history(Operation.SMOOTH), qualified)


def test_a_local_edit_is_never_allowed_however_it_was_qualified():
    qualified = frozenset(Operation)
    found = faults(history(Operation.LOCAL_EDIT), qualified)
    assert "not allowed on a graded image" in found[0]


def test_compressing_twice_is_reported_on_its_own():
    found = faults(
        history(Operation.LOSSY_COMPRESSION, Operation.LOSSY_COMPRESSION), viewing_only()
    )
    assert any("compressed more than once" in f for f in found)


def test_every_fault_is_reported_not_just_the_first():
    found = faults(history(Operation.SHARPEN, Operation.LOCAL_EDIT), viewing_only())
    assert len(found) == 2


def test_the_check_refuses_a_disqualified_image_and_says_why():
    with pytest.raises(QualificationError) as caught:
        check(history(Operation.LOCAL_EDIT), viewing_only())
    assert "not allowed" in caught.value.context["reasons"]


def test_the_check_passes_a_sound_history_silently():
    check(history(Operation.WINDOW), viewing_only())


def test_an_operation_is_applied_by_somebody():
    with pytest.raises(ValidationError):
        Applied(Operation.WINDOW, "", "  ")


def test_an_operation_prints_its_setting_when_it_has_one():
    assert applied(Operation.WINDOW, "1200/400").describe() == ("window and level 1200/400 by 0142")
    assert applied(Operation.MIRROR).describe() == "mirror by 0142"


def test_the_default_qualified_set_is_the_reversible_operations():
    assert viewing_only() == frozenset(op for op in Operation if op.is_reversible)
    assert Operation.SHARPEN not in viewing_only()
