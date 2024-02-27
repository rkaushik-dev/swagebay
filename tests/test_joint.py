import pytest

from qu_imager.errors import ValidationError
from qu_imager.geometry.joint import SMALL_BORE_LIMIT, Joint, JointForm
from qu_imager.materials import STEEL, material
from qu_imager.quantities.length import Length, inch, mm


def girth(od="168.3", wall="7.11", reinforcement="0"):
    return Joint(
        form=JointForm.PIPE_GIRTH,
        wall=mm(wall),
        material=STEEL,
        outside_diameter=mm(od),
        reinforcement=mm(reinforcement),
    )


def test_a_six_inch_line_gives_the_diameters_the_line_list_says():
    joint = girth()
    assert joint.od == mm("168.3")
    assert joint.inside_diameter == mm("154.08")
    assert joint.diameter_to_wall > 23


def test_circumferences_come_out_in_whole_micrometres():
    joint = girth()
    assert joint.outside_circumference == Length(528730)
    assert joint.inside_circumference < joint.outside_circumference
    assert joint.inside_circumference < joint.mean_circumference
    assert joint.mean_circumference < joint.outside_circumference


def test_reinforcement_is_measured_not_specified_and_adds_to_the_path():
    bare = girth(reinforcement="0")
    capped = girth(reinforcement="2.4")
    assert bare.total_thickness == mm("7.11")
    assert capped.total_thickness == mm("9.51")


def test_small_bore_is_decided_at_three_and_a_half_inches():
    assert inch("3.5") == SMALL_BORE_LIMIT
    assert girth(od="88.9").is_small_bore
    assert girth(od="60.3").is_small_bore
    assert not girth(od="88.91").is_small_bore
    assert not girth().is_small_bore


def test_plate_carries_no_diameter_and_says_so_when_asked():
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    assert not plate.is_small_bore
    with pytest.raises(ValidationError):
        plate.od
    with pytest.raises(ValidationError):
        plate.outside_circumference


def test_a_pipe_joint_without_a_diameter_is_refused_at_construction():
    with pytest.raises(ValidationError):
        Joint(form=JointForm.PIPE_GIRTH, wall=mm(7), material=STEEL)


def test_plate_with_a_diameter_is_refused_too():
    with pytest.raises(ValidationError):
        Joint(
            form=JointForm.PLATE_BUTT,
            wall=mm(12),
            material=STEEL,
            outside_diameter=mm(100),
        )


def test_a_wall_that_closes_the_bore_is_refused():
    with pytest.raises(ValidationError):
        girth(od="20", wall="10")
    with pytest.raises(ValidationError):
        girth(od="20", wall="12")


def test_a_zero_or_negative_wall_is_refused():
    with pytest.raises(ValidationError):
        Joint(form=JointForm.PLATE_BUTT, wall=Length.ZERO, material=STEEL)
    with pytest.raises(ValidationError):
        Joint(form=JointForm.PLATE_BUTT, wall=Length(-1), material=STEEL)


def test_negative_reinforcement_is_refused():
    with pytest.raises(ValidationError):
        Joint(
            form=JointForm.PLATE_BUTT,
            wall=mm(12),
            material=STEEL,
            reinforcement=Length(-1),
        )


def test_a_projector_head_fits_a_big_line_and_not_a_small_one():
    big = girth(od="323.9", wall="9.53")
    small = girth(od="33.4", wall="3.38")
    head = mm(25)
    clearance = mm(5)
    assert big.admits_internal_source(head, clearance)
    assert not small.admits_internal_source(head, clearance)


def test_only_the_round_forms_are_circumferential():
    assert JointForm.PIPE_GIRTH.is_circumferential
    assert JointForm.NOZZLE.is_circumferential
    assert JointForm.SOCKET.is_circumferential
    assert not JointForm.PLATE_BUTT.is_circumferential
    assert not JointForm.PIPE_SEAM.is_circumferential


def test_the_description_is_what_goes_on_the_technique_sheet():
    assert girth().describe() == "carbon steel girth weld, 168.30 mm od, 7.11 mm wall"
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=material("aluminium"))
    assert plate.describe() == "aluminium plate butt weld, 12.00 mm wall"
