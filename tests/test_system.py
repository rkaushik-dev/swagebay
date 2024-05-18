from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.computed import plate
from qu_imager.imaging.detector import detector
from qu_imager.imaging.film import film
from qu_imager.imaging.system import (
    FILM_RESOLUTION,
    ImagingSystem,
    Medium,
    density_applies,
    noise_applies,
)
from qu_imager.quantities.length import Length, mm


def test_a_film_system_carries_its_speed_and_no_noise_figure():
    system = ImagingSystem.from_film(film("d7"))
    assert system.medium is Medium.FILM
    assert system.speed_index == Decimal(100)
    assert system.normalised_snr is None


def test_a_plate_carries_its_noise_figure_and_no_speed():
    system = ImagingSystem.from_plate(plate("ip2_std"))
    assert system.medium is Medium.PLATE
    assert system.normalised_snr == 150
    assert system.speed_index is None


def test_a_panel_carries_its_own_resolution():
    system = ImagingSystem.from_detector(detector("dxr250"))
    assert system.medium is Medium.PANEL
    assert system.basic_spatial_resolution == mm("0.250")


def test_film_is_finer_than_anything_digital_in_the_shop():
    on_film = ImagingSystem.from_film(film("d4"))
    assert on_film.basic_spatial_resolution == FILM_RESOLUTION
    for key in ("ip1_hd", "ip2_std"):
        assert (
            on_film.basic_spatial_resolution
            < ImagingSystem.from_plate(plate(key)).basic_spatial_resolution
        )


def test_the_medium_decides_which_gate_runs():
    on_film = ImagingSystem.from_film(film("d7"))
    on_panel = ImagingSystem.from_detector(detector("dxr250"))
    assert density_applies(on_film)
    assert not noise_applies(on_film)
    assert noise_applies(on_panel)
    assert not density_applies(on_panel)


def test_the_gates_are_named_so_a_report_can_list_them():
    assert ImagingSystem.from_film(film("d7")).gates == (
        "density",
        "density variation",
        "indicator",
    )
    assert ImagingSystem.from_plate(plate("ip1_hd")).gates == (
        "normalised snr",
        "basic spatial resolution",
        "indicator",
    )


def test_the_indicator_gate_runs_on_everything():
    for system in (
        ImagingSystem.from_film(film("d7")),
        ImagingSystem.from_plate(plate("ip1_hd")),
        ImagingSystem.from_detector(detector("dxr250")),
    ):
        assert "indicator" in system.gates


def test_both_plate_and_panel_count_as_digital():
    assert Medium.PLATE.is_digital
    assert Medium.PANEL.is_digital
    assert not Medium.FILM.is_digital


def test_a_system_resolves_a_feature_at_or_above_its_resolution():
    system = ImagingSystem.from_detector(detector("dxr250"))
    assert system.resolves(mm("0.250"))
    assert system.resolves(mm("0.400"))
    assert not system.resolves(mm("0.100"))


def test_a_film_system_without_a_speed_index_is_refused():
    with pytest.raises(ValidationError):
        ImagingSystem(key="x", name="x", medium=Medium.FILM, basic_spatial_resolution=mm("0.03"))


def test_a_digital_system_without_a_noise_figure_is_refused():
    with pytest.raises(ValidationError):
        ImagingSystem(key="x", name="x", medium=Medium.PANEL, basic_spatial_resolution=mm("0.2"))
    with pytest.raises(ValidationError):
        ImagingSystem(key="x", name="x", medium=Medium.PLATE, basic_spatial_resolution=mm("0.06"))


def test_a_system_with_no_resolution_is_refused():
    with pytest.raises(ValidationError):
        ImagingSystem(
            key="x",
            name="x",
            medium=Medium.FILM,
            basic_spatial_resolution=Length.ZERO,
            speed_index=Decimal(100),
        )


def test_a_system_prints_what_it_is_and_what_it_is_on():
    assert str(ImagingSystem.from_film(film("d7"))) == "Structurix D7 on film"
    assert str(ImagingSystem.from_plate(plate("ip1_hd"))) == "HD-IP on computed radiography"
