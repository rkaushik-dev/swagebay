import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands

load_commands()


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    return run


def test_the_boundary_table_lists_every_posting(shop):
    output = shop("safety", "boundary", "--curies", "74").output
    lines = output.splitlines()
    assert lines[0].split()[0] == "posting"
    assert "restricted area" in output
    assert "high radiation area" in output


def test_an_open_beam_ropes_off_the_same_distance_all_round(shop):
    output = shop("safety", "boundary", "--curies", "74").output
    assert "restricted area at" in output
    assert "down the beam" not in output


def test_a_collimator_makes_the_rope_directional(shop):
    output = shop("safety", "boundary", "--curies", "74", "--collimator", "w60").output
    assert "down the beam" in output
    assert "to the sides" in output


def test_a_barrier_pulls_the_boundary_in(shop):
    bare = shop("safety", "boundary", "--curies", "74").output
    shielded = shop(
        "safety",
        "boundary",
        "--curies",
        "74",
        "--barrier",
        "lead",
        "--thickness",
        "30 mm",
    ).output
    assert bare != shielded


def test_a_barrier_with_no_thickness_is_a_usage_error(shop):
    result = shop("safety", "boundary", "--curies", "74", "--barrier", "lead")
    assert result.exception is not None


def test_a_hotter_source_pushes_the_rope_out(shop):
    small = shop("safety", "boundary", "--curies", "20").output
    big = shop("safety", "boundary", "--curies", "120").output
    assert small != big


def test_an_isotope_nothing_knows_about_is_refused(shop):
    assert shop("safety", "boundary", "--curies", "74", "--isotope", "ra226").exception


def test_the_shielding_table_gives_both_layers(shop):
    output = shop("safety", "shielding").output
    assert "half value layer" in output
    assert "tenth value layer" in output
    assert "4.80 mm" in output


def test_the_shielding_table_takes_another_material(shop):
    lead = shop("safety", "shielding", "--material", "lead").output
    concrete = shop("safety", "shielding", "--material", "concrete").output
    assert lead != concrete
    assert "44.50 mm" in concrete


def test_a_material_with_no_published_layer_is_refused(shop):
    assert shop("safety", "shielding", "--material", "gypsum").exception


def test_cobalt_needs_more_lead_than_iridium(shop):
    iridium = shop("safety", "shielding", "--isotope", "ir192").output
    cobalt = shop("safety", "shielding", "--isotope", "co60").output
    assert "12.50 mm" in cobalt
    assert "12.50 mm" not in iridium


def test_the_collimator_list_names_every_cup(shop):
    output = shop("safety", "collimators").output
    assert "w60" in output
    assert "du45" in output
    assert "tungsten" in output


def test_the_collimator_list_shows_what_each_one_transmits(shop):
    output = shop("safety", "collimators").output
    assert "0.06" in output
    assert "0.03" in output
