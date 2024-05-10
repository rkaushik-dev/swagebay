from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.imaging.detector import (
    MAX_ACCEPTABLE_BURN_IN,
    DetectorArray,
    detector,
    detectors_meeting,
    known_detectors,
)
from qu_imager.quantities.length import Length, mm


def test_resolution_is_always_worse_than_the_pitch():
    for key in known_detectors():
        panel = detector(key)
        assert panel.basic_spatial_resolution > panel.pixel_pitch
        assert panel.resolution_penalty > 1


def test_the_datasheet_lie_is_measurable():
    panel = detector("flex7")
    assert panel.pixel_pitch == mm("0.127")
    assert panel.basic_spatial_resolution == mm("0.200")
    assert Decimal("1.5") < panel.resolution_penalty < Decimal("1.6")


def test_a_panel_reports_its_pixel_count_and_its_active_area():
    panel = detector("dxr250")
    assert panel.pixels == 1024 * 1024
    width, height = panel.active_area
    assert width == mm("204.8")
    assert height == mm("204.8")


def test_a_rectangular_panel_has_a_rectangular_area():
    width, height = detector("flex7").active_area
    assert width > height
    assert detector("flex7").pixels == 2048 * 1536


def test_lag_inside_the_limit_passes_and_outside_it_does_not():
    assert detector("dxr250").lag_is_acceptable()
    assert detector("xrd1611").lag_is_acceptable()
    assert not detector("flex7").lag_is_acceptable()


def test_burn_in_is_what_scraps_a_panel():
    assert not detector("dxr250").is_scrap()
    scrap = DetectorArray(
        key="tired",
        name="tired",
        manufacturer="x",
        pixel_pitch=mm("0.200"),
        basic_spatial_resolution=mm("0.250"),
        normalised_snr=200,
        efficiency=Decimal("0.7"),
        image_lag=Decimal("0.01"),
        burn_in=MAX_ACCEPTABLE_BURN_IN + Decimal("0.001"),
        columns=1024,
        rows=1024,
    )
    assert scrap.is_scrap()
    assert any("burn in" in s for s in scrap.shortfalls(mm(1), 100))


def test_a_panel_can_fail_on_lag_alone_while_meeting_the_specification():
    panel = detector("flex7")
    shortfalls = panel.shortfalls(mm("0.200"), 150)
    assert len(shortfalls) == 1
    assert "image lag" in shortfalls[0]


def test_a_panel_that_clears_everything_reports_nothing():
    assert detector("dxr250").shortfalls(mm("0.250"), 200) == ()


def test_both_the_resolution_and_the_noise_shortfall_are_reported():
    shortfalls = detector("dxr250").shortfalls(mm("0.160"), 300)
    assert len(shortfalls) == 2
    assert "coarser" in shortfalls[0]
    assert "normalised snr" in shortfalls[1]


def test_the_stock_that_meets_a_specification_comes_back_coarsest_first():
    fits = detectors_meeting(mm("0.250"), 180)
    assert [d.key for d in fits] == ["dxr250", "xrd1611"]
    assert detectors_meeting(mm("0.050"), 100) == ()


def test_a_panel_claiming_resolution_finer_than_its_pitch_is_refused():
    with pytest.raises(ValidationError) as caught:
        DetectorArray(
            key="liar",
            name="liar",
            manufacturer="x",
            pixel_pitch=mm("0.200"),
            basic_spatial_resolution=mm("0.100"),
            normalised_snr=200,
            efficiency=Decimal("0.7"),
            image_lag=Decimal("0.01"),
            burn_in=Decimal("0.001"),
            columns=1024,
            rows=1024,
        )
    assert caught.value.context["pitch_um"] == 200


def test_a_panel_with_no_pitch_or_no_size_is_refused():
    def build(**overrides):
        base = {
            "key": "x",
            "name": "x",
            "manufacturer": "x",
            "pixel_pitch": mm("0.200"),
            "basic_spatial_resolution": mm("0.250"),
            "normalised_snr": 200,
            "efficiency": Decimal("0.7"),
            "image_lag": Decimal("0.01"),
            "burn_in": Decimal("0.001"),
            "columns": 1024,
            "rows": 1024,
        }
        base.update(overrides)
        return DetectorArray(**base)

    with pytest.raises(ValidationError):
        build(pixel_pitch=Length.ZERO)
    with pytest.raises(ValidationError):
        build(columns=0)
    with pytest.raises(ValidationError):
        build(image_lag=Decimal("1.5"))
    with pytest.raises(ValidationError):
        build(burn_in=Decimal(-1))


def test_the_catalogue_is_closed():
    assert known_detectors() == ("dxr250", "flex7", "xrd1611")
    with pytest.raises(ConfigurationError):
        detector("dxr999")
    assert str(detector("dxr250")) == "DXR250C 1024x1024"
