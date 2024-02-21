from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ValidationError
from qu_imager.materials import STEEL, Quality, known_materials, material
from qu_imager.quantities.length import Length, inch, mm


def test_steel_is_one_at_every_published_quality():
    for quality in Quality:
        assert STEEL.factor_at(quality) == Decimal(1)


def test_steel_equivalent_of_steel_is_the_thickness_itself():
    wall = inch("0.375")
    assert STEEL.steel_equivalent(wall, Quality.IR192) == wall


def test_aluminium_shoots_like_a_third_of_the_steel_under_iridium():
    alu = material("aluminium")
    assert alu.factor_at(Quality.IR192) == Decimal("0.35")
    assert alu.steel_equivalent(mm(20), Quality.IR192) == mm(7)


def test_the_factor_depends_on_the_beam_and_not_only_the_metal():
    alu = material("aluminium")
    assert alu.factor_at(Quality.KV100) == Decimal("0.08")
    assert alu.factor_at(Quality.KV220) == Decimal("0.18")
    assert alu.factor_at(Quality.KV100) != alu.factor_at(Quality.IR192)


def test_a_hole_in_the_published_table_is_reported_not_guessed():
    magnesium = material("magnesium")
    with pytest.raises(ConfigurationError) as caught:
        magnesium.factor_at(Quality.IR192)
    assert caught.value.context["material"] == "magnesium"
    assert caught.value.context["quality"] == "Ir-192"


def test_the_heavy_metals_go_the_other_way():
    lead = material("lead")
    assert lead.factor_at(Quality.KV100) == Decimal("14.0")
    assert lead.steel_equivalent(mm(1), Quality.KV100) == mm(14)
    assert lead.factor_at(Quality.CO60) < lead.factor_at(Quality.IR192)


def test_inconel_is_flat_across_the_whole_range():
    inconel = material("inconel")
    values = {inconel.factor_at(q) for q in Quality if q is not Quality.KV100}
    assert values == {Decimal("1.3")}


def test_stainless_reads_as_steel_but_is_a_separate_entry():
    assert material("stainless_304").factor_at(Quality.IR192) == Decimal(1)
    assert material("stainless_304") is not STEEL
    assert material("stainless_304").density_kg_m3 == 8000


def test_ferrous_is_a_property_of_the_material_not_the_factor():
    assert STEEL.is_ferrous()
    assert material("stainless_316").is_ferrous()
    assert not material("aluminium").is_ferrous()
    assert not material("inconel").is_ferrous()


def test_a_negative_thickness_is_refused_before_the_lookup():
    with pytest.raises(ValidationError):
        STEEL.steel_equivalent(Length(-1), Quality.IR192)


def test_the_catalogue_is_closed():
    with pytest.raises(ConfigurationError):
        material("unobtainium")
    keys = known_materials()
    assert "carbon_steel" in keys
    assert keys == tuple(sorted(keys))
    assert len(keys) == 12


def test_a_material_prints_as_the_name_a_welder_would_use():
    assert str(material("aluminium_2024")) == "aluminium 2024"
    assert str(Quality.MV4_25) == "4 to 25 MV"
    assert str(STEEL) == "carbon steel"
