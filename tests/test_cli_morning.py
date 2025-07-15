import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands

load_commands()

ASSAY = "2025-06-01T00:00:00"
FRESH = "2025-07-01T00:00:00"
DAY = "2025-07-10"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    run("source", "add-device", "D-77", "880d")
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
    return run


def ready(shop):
    shop("source", "wipe", "IR-5512", "--at", FRESH, "--by", "rp")
    shop("device", "inspect", "D-77", "--quarterly", "--on", "2025-06-20", "--by", "0303")
    shop("device", "inspect", "D-77", "--on", DAY, "--by", "0142")


def test_a_quiet_morning_exits_zero(shop):
    ready(shop)
    result = shop("morning", "--on", DAY)
    assert result.exit_code == 0
    assert "nothing outstanding" in result.output


def test_a_device_with_no_inspection_stops_the_day(shop):
    shop("source", "wipe", "IR-5512", "--at", FRESH, "--by", "rp")
    result = shop("morning", "--on", DAY)
    assert result.exit_code == 4
    assert "stops work today" in result.output
    assert "no quarterly inspection on record" in result.output


def test_a_source_with_no_wipe_test_stops_the_day(shop):
    shop("device", "inspect", "D-77", "--quarterly", "--on", "2025-06-20", "--by", "0303")
    shop("device", "inspect", "D-77", "--on", DAY, "--by", "0142")
    result = shop("morning", "--on", DAY)
    assert result.exit_code == 4
    assert "no wipe test on record" in result.output


def test_the_loads_flag_lists_what_could_go_out(shop):
    ready(shop)
    result = shop("morning", "--on", DAY, "--loads")
    assert "ready to go out" in result.output
    assert "D-77" in result.output
    assert "IR-5512" in result.output


def test_the_loads_flag_says_so_when_nothing_can_go(shop):
    result = shop("morning", "--on", DAY, "--loads")
    assert "nothing is cleared to leave the shop" in result.output


def test_the_loads_are_not_listed_without_the_flag(shop):
    ready(shop)
    assert "ready to go out" not in shop("morning", "--on", DAY).output


def test_an_overdue_report_is_listed_as_late_and_still_stops_the_script(shop):
    ready(shop)
    shop(
        "ncr",
        "raise",
        "NCR-1",
        "--job",
        "24-0001",
        "--item",
        "GW-104",
        "--requirement",
        "API 1104 9.3.8",
        "--account",
        "slag",
        "--on",
        "2025-05-01",
        "--by",
        "0142",
    )
    result = shop("morning", "--on", DAY)
    assert result.exit_code == 4
    assert "running late" in result.output
    assert "stops work today" not in result.output
