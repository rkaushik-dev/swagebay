import pytest

from qu_imager.evaluation.taxonomy import PLANAR, VOLUMETRIC, Kind, by_code, describe


def test_the_planar_kinds_are_the_sharp_edged_ones():
    assert Kind.C.is_planar
    assert Kind.IP.is_planar
    assert Kind.IF.is_planar
    assert Kind.HB.is_planar


def test_the_volumetric_kinds_are_the_rounded_ones():
    assert Kind.P.is_volumetric
    assert Kind.ESI.is_volumetric
    assert Kind.ISI.is_volumetric
    assert not Kind.P.is_planar


def test_every_kind_is_one_or_the_other_and_never_both():
    for kind in Kind:
        assert kind.is_planar != kind.is_volumetric
    assert len(PLANAR) + len(VOLUMETRIC) == len(list(Kind))


def test_only_porosity_is_rounded():
    rounded = {k for k in Kind if k.is_rounded}
    assert rounded == {Kind.P, Kind.CP}


def test_the_surface_breaking_kinds_are_the_ones_a_finger_would_find():
    assert Kind.U.is_surface_breaking
    assert Kind.BT.is_surface_breaking
    assert Kind.IC.is_surface_breaking
    assert not Kind.ISI.is_surface_breaking


def test_a_crack_is_never_acceptable_at_any_length():
    assert Kind.C.is_always_rejectable
    for kind in Kind:
        if kind is not Kind.C:
            assert not kind.is_always_rejectable


def test_the_aggregated_kinds_add_up_against_a_length_of_weld():
    assert Kind.ESI.is_aggregated
    assert Kind.IP.is_aggregated
    assert Kind.U.is_aggregated
    assert not Kind.P.is_aggregated
    assert not Kind.ISI.is_aggregated


def test_the_two_kinds_of_inadequate_penetration_are_separate_entries():
    assert Kind.IP is not Kind.IPD
    assert Kind.IP.is_planar
    assert Kind.IPD.is_planar
    assert "high low" in Kind.IPD.value


def test_the_two_kinds_of_incomplete_fusion_are_separate_too():
    assert Kind.IF is not Kind.IFD
    assert "cold lap" in Kind.IFD.value


def test_a_code_reads_back_into_a_kind():
    assert by_code("ESI") is Kind.ESI
    assert by_code(" ip ") is Kind.IP
    assert by_code("c") is Kind.C


def test_an_unknown_code_is_refused():
    with pytest.raises(ValueError):
        by_code("XX")
    with pytest.raises(ValueError):
        by_code("")


def test_the_code_is_what_goes_on_the_report():
    assert Kind.ESI.code == "ESI"
    assert str(Kind.IPD) == "IPD"
    assert describe(Kind.C) == "C, crack, planar"
    assert describe(Kind.P) == "P, porosity, volumetric"
