import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import NotFoundError, ValidationError

load_commands()

ASSAY = "2024-11-01T00:00:00"
SHOT = "2024-11-21T23:30:00"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    run("source", "add-device", "880D-221", "880d")
    run(
        "source",
        "register",
        "IR-5512",
        "--isotope",
        "ir192",
        "--curies",
        "90",
        "--assayed",
        ASSAY,
    )
    run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp")
    run(
        "job",
        "open",
        "--client",
        "c",
        "--site",
        "s",
        "--procedure",
        "RT-001",
        "--on",
        "2024-11-01",
    )
    run(
        "weld",
        "add",
        "24-0001",
        "GW-104",
        "--wall",
        "10.97 mm",
        "--diameter",
        "168.3 mm",
        "--cap",
        "2 mm",
        "--length",
        "529 mm",
        "--welder",
        "w-19",
        "--on",
        "2024-11-01",
    )
    return run


def plan(run, *extra):
    return run(
        "technique",
        "plan",
        "24-0001",
        "GW-104",
        "--source",
        "IR-5512",
        "--at",
        SHOT,
        *extra,
    )


def test_a_sheet_prints_every_number_the_crew_needs(shop):
    result = plan(shop, "--shot", "double wall single image")
    assert result.exit_code == 0
    output = result.output
    assert "technique sheet GW-104" in output
    assert "penetrated      25.94 mm" in output
    assert "exposures       8" in output
    assert "clean           yes" in output


def test_the_planner_picks_the_shot_when_none_is_named(shop):
    output = plan(shop).output
    assert "shot            panoramic" in output
    assert "exposures       1" in output


def test_the_shot_can_be_named_by_its_enum_name_too(shop):
    output = plan(shop, "--shot", "DWSI").output
    assert "double wall single image" in output


def test_a_shot_type_nothing_knows_about_is_refused(shop):
    result = plan(shop, "--shot", "sideways")
    assert isinstance(result.exception, ValidationError)


def test_warnings_print_under_a_blank_line(shop):
    result = plan(shop, "--shot", "double wall single image", "--window", "1")
    lines = result.output.splitlines()
    blank = lines.index("")
    assert any("source out" in line for line in lines[blank:])


def test_the_film_side_flag_asks_for_the_marker(shop):
    output = plan(shop, "--shot", "double wall single image", "--film-side").output
    assert "F marker" in output


def test_a_slower_film_gives_a_longer_sheet(shop):
    fast = plan(shop, "--shot", "double wall single image", "--film", "d7").output
    slow = plan(shop, "--shot", "double wall single image", "--film", "d4").output
    assert fast != slow


def test_a_weld_that_is_not_there_is_not_found(shop):
    result = shop(
        "technique",
        "plan",
        "24-0001",
        "GW-999",
        "--source",
        "IR-5512",
        "--at",
        SHOT,
    )
    assert isinstance(result.exception, NotFoundError)


def test_films_are_compared_in_a_table(shop):
    result = shop(
        "technique",
        "compare",
        "24-0001",
        "GW-104",
        "--source",
        "IR-5512",
        "--films",
        "d7,d5,d4",
        "--at",
        SHOT,
        "--shot",
        "double wall single image",
    )
    lines = result.output.splitlines()
    assert lines[0].split() == ["film", "each", "n", "source", "out", "clean"]
    assert lines[2].startswith("d7")
    assert lines[4].startswith("d4")


def test_comparing_no_films_is_refused(shop):
    result = shop(
        "technique",
        "compare",
        "24-0001",
        "GW-104",
        "--source",
        "IR-5512",
        "--films",
        " , ",
        "--at",
        SHOT,
    )
    assert isinstance(result.exception, ValidationError)


def test_the_comparison_only_prints_what_changes_between_films(shop):
    output = shop(
        "technique",
        "compare",
        "24-0001",
        "GW-104",
        "--source",
        "IR-5512",
        "--films",
        "d7,d4",
        "--at",
        SHOT,
        "--shot",
        "double wall single image",
    ).output
    assert "unsharpness" not in output
    assert "penetrated" not in output
