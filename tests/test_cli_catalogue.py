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


def test_the_isotope_table_lists_every_isotope(shop):
    output = shop("catalogue", "isotopes").output
    for symbol in ("Ir-192", "Se-75", "Co-60", "Yb-169", "Cs-137"):
        assert symbol in output


def test_the_isotope_table_carries_the_numbers_off_the_card(shop):
    output = shop("catalogue", "isotopes").output
    assert "73.83" in output
    assert "0.48" in output


def test_asking_what_covers_a_wall_narrows_the_list(shop):
    output = shop("catalogue", "isotopes", "--covering", "20 mm").output
    assert "Se-75" in output
    assert "Ir-192" in output
    assert "Co-60" not in output


def test_the_covering_list_comes_back_softest_first(shop):
    lines = shop("catalogue", "isotopes", "--covering", "26 mm").output.splitlines()
    assert lines[2].startswith("Se-75")
    assert lines[3].startswith("Ir-192")


def test_a_wall_nothing_covers_comes_back_empty(shop):
    output = shop("catalogue", "isotopes", "--covering", "500 mm").output
    assert "Ir-192" not in output


def test_a_thickness_that_will_not_parse_is_refused(shop):
    assert shop("catalogue", "isotopes", "--covering", "twenty mm").exception


def test_the_film_table_lists_the_stock_and_the_speeds(shop):
    output = shop("catalogue", "films").output
    assert "Structurix D7" in output
    assert "Industrex AA400" in output
    assert "100" in output


def test_the_material_table_takes_a_beam_quality(shop):
    iridium = shop("catalogue", "materials").output
    soft = shop("catalogue", "materials", "--quality", "KV100").output
    assert "aluminium" in iridium
    assert iridium != soft


def test_a_hole_in_the_equivalence_table_is_printed_as_such(shop):
    output = shop("catalogue", "materials", "--quality", "IR192").output
    assert "not published" in output


def test_the_indicator_list_carries_both_families(shop):
    output = shop("catalogue", "indicators").output
    assert "hole identities" in output
    assert "W1" in output
    assert "3.200 mm" in output


def test_the_chart_list_names_what_each_one_was_drawn_for(shop):
    output = shop("catalogue", "charts").output
    assert "ir192-steel-d7-2.0" in output
    assert "co60-steel-d7-2.0" in output
    assert "24 in" in output
    assert "36 in" in output


def test_the_chart_list_shows_the_range_each_one_covers(shop):
    output = shop("catalogue", "charts").output
    assert "6.4 mm" in output
    assert "63.5 mm" in output
