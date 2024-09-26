import pytest

from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QualificationError,
    QuImagerError,
    SafetyError,
    TechniqueError,
    UnitError,
    ValidationError,
)

ALL_ERRORS = [
    QuImagerError,
    ConfigurationError,
    ValidationError,
    UnitError,
    TechniqueError,
    QualificationError,
    SafetyError,
    NotFoundError,
    ConflictError,
]


def test_every_error_descends_from_the_base():
    for cls in ALL_ERRORS:
        assert issubclass(cls, QuImagerError)
        assert issubclass(cls, Exception)


def test_codes_are_unique_and_namespaced():
    codes = [cls.code for cls in ALL_ERRORS]
    assert len(codes) == len(set(codes))
    for code in codes:
        assert code.startswith("qi.")


def test_a_unit_error_is_a_validation_error():
    assert issubclass(UnitError, ValidationError)
    with pytest.raises(ValidationError):
        raise UnitError("cannot add curies to millimetres")


def test_message_alone_renders_plainly():
    err = TechniqueError("source to film distance below the minimum")
    assert str(err) == "source to film distance below the minimum"


def test_context_is_appended_in_a_stable_order():
    err = TechniqueError("too close", sfd_mm=400, minimum_mm=610)
    assert str(err) == "too close (minimum_mm=610, sfd_mm=400)"


def test_as_dict_carries_the_code_and_a_copy_of_the_context():
    err = QualificationError("density below range", density=1.4, floor=1.8)
    body = err.as_dict()
    assert body == {
        "code": "qi.qualification",
        "message": "density below range",
        "context": {"density": 1.4, "floor": 1.8},
    }
    body["context"]["density"] = 9.9
    assert err.context["density"] == 1.4


def test_not_found_builds_its_own_message():
    err = NotFoundError("source", "IR-2211")
    assert err.kind == "source"
    assert err.identity == "IR-2211"
    assert "no source with identity 'IR-2211'" in str(err)


def test_safety_and_conflict_are_distinct_branches():
    assert not issubclass(SafetyError, ConflictError)
    assert not issubclass(ConflictError, SafetyError)


def test_raising_the_base_still_carries_context():
    with pytest.raises(QuImagerError) as caught:
        raise QuImagerError("something", weld="GW-104")
    assert caught.value.context == {"weld": "GW-104"}


def test_a_validation_error_is_also_a_value_error():
    assert issubclass(ValidationError, ValueError)
    assert issubclass(UnitError, ValueError)
    with pytest.raises(ValueError):
        raise ValidationError("that is not a length")


def test_the_other_branches_are_not_value_errors():
    for cls in (TechniqueError, QualificationError, SafetyError, ConflictError):
        assert not issubclass(cls, ValueError)
