from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.viewing import (
    MAXIMUM_AMBIENT_FRACTION,
    MINIMUM_TRANSMITTED,
    Viewer,
    faults,
    known_viewers,
    maximum_ambient,
    viewer,
    viewers_supporting,
)


def test_the_supported_density_falls_out_of_the_luminance():
    assert viewer("hi300").supported_density == Decimal(3)
    assert viewer("bench").supported_density == Decimal(2)


def test_a_brighter_viewer_supports_a_denser_film():
    assert viewer("hi300").supported_density > viewer("hi100").supported_density
    assert viewer("hi100").supported_density > viewer("bench").supported_density


def test_a_field_viewer_will_not_read_a_code_density_film():
    assert not viewer("field").supports(Decimal("2.0"))
    assert viewer("field").supports(Decimal("1.5"))


def test_the_transmitted_light_falls_by_a_decade_per_unit_of_density():
    bright = viewer("hi300")
    assert bright.transmitted_at(Decimal(0)) == Decimal(30000)
    assert bright.transmitted_at(Decimal(1)) == Decimal(3000)
    assert bright.transmitted_at(Decimal(2)) == Decimal(300)
    assert bright.transmitted_at(Decimal(3)) == MINIMUM_TRANSMITTED


def test_a_negative_density_is_refused():
    with pytest.raises(ValidationError):
        viewer("hi300").transmitted_at(Decimal(-1))


def test_a_viewer_with_no_luminance_is_refused():
    with pytest.raises(ValidationError):
        Viewer("x", "x", 0, True)


def test_the_room_has_to_be_a_hundredth_of_what_comes_through():
    allowed = maximum_ambient(viewer("hi300"), Decimal(2))
    assert allowed == Decimal(3)
    assert allowed == viewer("hi300").transmitted_at(Decimal(2)) * MAXIMUM_AMBIENT_FRACTION


def test_a_denser_film_needs_a_darker_room():
    bright = viewer("hi300")
    assert maximum_ambient(bright, Decimal(3)) < maximum_ambient(bright, Decimal(2))


def test_a_sound_combination_reports_no_faults():
    assert faults(viewer("hi300"), Decimal("2.5"), Decimal("0.5")) == ()


def test_a_film_too_dense_for_the_viewer_is_a_fault():
    found = faults(viewer("bench"), Decimal("3.0"), Decimal("0.01"))
    assert any("this viewer supports" in f for f in found)


def test_a_bright_room_is_a_fault_on_its_own():
    found = faults(viewer("hi300"), Decimal("2.5"), Decimal(100))
    assert len(found) == 1
    assert "ambient light" in found[0]


def test_a_thin_film_on_an_unmasked_viewer_is_a_fault_too():
    found = faults(viewer("bench"), Decimal("1.8"), Decimal("0.001"))
    assert any("washed out by the surround" in f for f in found)


def test_a_thin_film_on_a_masked_viewer_is_not():
    assert faults(viewer("hi100"), Decimal("1.8"), Decimal("0.001")) == ()


def test_every_fault_is_reported_not_just_the_first():
    found = faults(viewer("field"), Decimal("1.9"), Decimal(1000))
    assert len(found) == 3


def test_the_viewers_that_can_read_a_density_come_back_dimmest_first():
    fits = viewers_supporting(Decimal("2.0"))
    assert [v.key for v in fits] == ["bench", "hi100", "hi300"]
    assert viewers_supporting(Decimal("4.0")) == ()


def test_the_catalogue_is_closed():
    assert known_viewers() == ("bench", "field", "hi100", "hi300")
    with pytest.raises(ConfigurationError):
        viewer("hi999")
    assert str(viewer("hi300")) == "HiLight 300 30000 cd/m2 masked"
