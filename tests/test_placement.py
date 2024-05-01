import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import ClockPosition
from qu_imager.imaging.placement import (
    Shim,
    check_shim_covers,
    indicators_required,
    minimum_shim_size,
    panoramic_positions,
    plan_placements,
    shim_for,
)
from qu_imager.imaging.selection import IndicatorSide
from qu_imager.materials import STEEL
from qu_imager.quantities.length import Length, mm


def capped(reinforcement="2.5"):
    return ps.girth_weld("6", "40", STEEL, reinforcement=mm(reinforcement))


def test_a_capped_weld_needs_a_shim_the_height_of_the_cap():
    shim = shim_for(capped("2.5"))
    assert shim is not None
    assert shim.thickness == mm("2.5")
    assert shim.material_key == "carbon_steel"


def test_a_ground_flush_weld_needs_no_shim():
    assert shim_for(capped("0")) is None


def test_a_shim_with_no_thickness_is_not_a_shim():
    with pytest.raises(ValidationError):
        Shim(Length.ZERO, "carbon_steel")


def test_the_shim_has_to_stand_proud_on_every_side():
    assert minimum_shim_size(mm(25)) == mm(31)
    check_shim_covers(mm(25), mm(31))
    with pytest.raises(TechniqueError) as caught:
        check_shim_covers(mm(25), mm(28))
    assert caught.value.context["needed_mm"] == "31"


def test_a_plaque_with_no_size_is_refused():
    with pytest.raises(ValidationError):
        minimum_shim_size(Length.ZERO)


def test_one_indicator_per_radiograph_is_the_rule():
    assert indicators_required(ShotType.DWSI, 3) == 3
    assert indicators_required(ShotType.DWDI_ELLIPTICAL, 2) == 2
    assert indicators_required(ShotType.CONTACT, 1) == 1


def test_a_panoramic_shot_takes_three_however_many_exposures_it_is():
    assert indicators_required(ShotType.PANORAMIC, 1) == 3
    assert indicators_required(ShotType.PANORAMIC, 2) == 3


def test_a_shot_count_of_nothing_is_refused():
    with pytest.raises(ValidationError):
        indicators_required(ShotType.DWSI, 0)
    with pytest.raises(ValidationError):
        panoramic_positions(0)


def test_the_three_panoramic_indicators_sit_a_third_of_the_way_round():
    positions = panoramic_positions()
    assert [str(p) for p in positions] == ["12:00", "4:00", "8:00"]
    assert positions[0] == ClockPosition.parse("12:00")


def test_a_panoramic_plan_puts_a_shim_under_every_one():
    placements = plan_placements(capped(), ShotType.PANORAMIC, 1)
    assert len(placements) == 3
    assert all(p.shim is not None for p in placements)
    assert [str(p.position) for p in placements] == ["12:00", "4:00", "8:00"]


def test_a_double_wall_plan_spaces_them_the_way_the_shots_are_spaced():
    placements = plan_placements(capped(), ShotType.DWSI, 3)
    assert [str(p.position) for p in placements] == ["12:00", "4:00", "8:00"]


def test_an_elliptical_plan_puts_two_at_right_angles():
    small = ps.girth_weld("1", "80", STEEL)
    placements = plan_placements(small, ShotType.DWDI_ELLIPTICAL, 2)
    assert [str(p.position) for p in placements] == ["12:00", "6:00"]


def test_a_plate_weld_gets_no_clock_positions_at_all():
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    placements = plan_placements(plate, ShotType.CONTACT, 2)
    assert len(placements) == 2
    assert all(p.position is None for p in placements)


def test_a_film_side_placement_carries_the_f_marker():
    placements = plan_placements(capped(), ShotType.DWSI, 3, side=IndicatorSide.FILM)
    assert all(p.needs_f_marker for p in placements)
    assert "F marker" in placements[0].describe()


def test_the_default_side_is_the_source_side():
    placement = plan_placements(capped(), ShotType.DWSI, 3)[0]
    assert placement.side is IndicatorSide.SOURCE
    assert not placement.needs_f_marker


def test_the_placement_prints_the_way_the_sheet_reads():
    placement = plan_placements(capped("2.5"), ShotType.DWSI, 3)[0]
    assert placement.describe() == "source side at 12:00 on a 2.50 mm carbon_steel shim"
    flush = plan_placements(capped("0"), ShotType.DWSI, 3)[0]
    assert flush.describe() == "source side at 12:00"
