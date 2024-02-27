from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.joint import JointForm
from qu_imager.materials import STEEL
from qu_imager.quantities.length import mm


def test_nominal_is_not_a_measurement():
    assert ps.outside_diameter("2") == mm("60.3")
    assert ps.outside_diameter("1/2") == mm("21.3")
    assert ps.outside_diameter("6") == mm("168.3")


def test_outside_diameter_does_not_move_with_the_schedule():
    for schedule in ("STD", "XS", "40", "80", "160"):
        size = ps.resolve("6", schedule)
        assert size.outside_diameter == mm("168.3")


def test_a_heavier_schedule_eats_the_bore():
    light = ps.resolve("6", "40")
    heavy = ps.resolve("6", "160")
    assert heavy.wall > light.wall
    assert heavy.inside_diameter < light.inside_diameter
    assert light.wall == mm("7.11")
    assert heavy.wall == mm("18.26")


def test_standard_and_schedule_forty_part_company_above_ten_inch():
    assert ps.wall("8", "STD") == ps.wall("8", "40")
    assert ps.wall("12", "STD") != ps.wall("12", "40")
    assert ps.wall("12", "STD") == mm("9.53")
    assert ps.wall("12", "40") == mm("10.31")


def test_standard_wall_is_flat_from_twelve_inch_up():
    walls = {ps.wall(n, "STD") for n in ("12", "14", "16", "18", "20", "24")}
    assert walls == {mm("9.53")}


def test_the_schedule_name_is_case_insensitive():
    assert ps.wall("6", "std") == ps.wall("6", "STD")
    assert ps.resolve("6", "xs").schedule == "XS"


def test_an_unpublished_combination_is_refused_rather_than_interpolated():
    with pytest.raises(ConfigurationError) as caught:
        ps.wall("14", "XXS")
    assert caught.value.context["schedule"] == "XXS"
    with pytest.raises(ConfigurationError):
        ps.wall("7", "40")
    with pytest.raises(ConfigurationError):
        ps.outside_diameter("7")


def test_the_table_covers_the_sizes_the_shop_meets():
    sizes = ps.nominal_sizes()
    assert sizes[0] == "1/2"
    assert sizes[-1] == "24"
    assert len(sizes) == 19
    for nominal in sizes:
        assert ps.wall(nominal, "STD").um > 0


def test_a_line_list_line_becomes_a_joint():
    joint = ps.girth_weld("6", "40", STEEL, reinforcement=mm("2.0"))
    assert joint.form is JointForm.PIPE_GIRTH
    assert joint.wall == mm("7.11")
    assert joint.od == mm("168.3")
    assert joint.total_thickness == mm("9.11")
    assert not joint.is_small_bore


def test_small_lines_come_back_small():
    joint = ps.girth_weld("2", "80", STEEL)
    assert joint.is_small_bore
    assert joint.wall == mm("5.54")


def test_a_tape_measure_reading_finds_its_nominal_size():
    assert ps.nearest_nominal(mm("168.3")) == "6"
    assert ps.nearest_nominal(mm("169.0")) == "6"
    assert ps.nearest_nominal(mm("60.0")) == "2"


def test_a_reading_between_two_sizes_is_refused_when_it_is_too_far_off():
    with pytest.raises(ConfigurationError):
        ps.nearest_nominal(mm("900"))
    assert ps.nearest_nominal(mm("900"), tolerance=Decimal("1")) == "24"


def test_a_resolved_size_prints_the_way_the_work_order_reads():
    assert str(ps.resolve("1-1/2", "80")) == "NPS 1-1/2 sch 80"
