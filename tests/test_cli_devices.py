import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ValidationError

load_commands()

ASSAY = "2025-06-01T00:00:00"
STRIPPED = "2025-06-02"
MORNING = "2025-06-24"


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
    run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp")
    return run


def test_a_clean_pre_use_check_is_recorded(shop):
    result = shop("device", "inspect", "D-77", "--on", MORNING, "--by", "0142")
    assert result.exit_code == 0
    assert "pre use passed by 0142" in result.output


def test_a_quarterly_is_recorded_as_a_quarterly(shop):
    result = shop("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303")
    assert "quarterly passed by 0303" in result.output


def test_a_defect_on_a_safety_item_takes_it_out_of_service(shop):
    result = shop(
        "device",
        "inspect",
        "D-77",
        "--on",
        MORNING,
        "--by",
        "0142",
        "--defect",
        "drive_cable:three strands broken",
    )
    assert "out of service" in result.output
    assert "pre use failed" in result.output


def test_a_defect_on_the_wheels_is_only_noted(shop):
    result = shop(
        "device",
        "inspect",
        "D-77",
        "--quarterly",
        "--on",
        STRIPPED,
        "--by",
        "0303",
        "--defect",
        "wheels:one castor seized",
    )
    assert "noted" in result.output
    assert "out of service" not in result.output


def test_an_item_nothing_inspects_is_refused_with_the_ones_that_exist(shop):
    result = shop(
        "device",
        "inspect",
        "D-77",
        "--on",
        MORNING,
        "--by",
        "0142",
        "--defect",
        "flux_capacitor:humming",
    )
    assert isinstance(result.exception, ValidationError)
    assert "drive cable" in result.exception.context["known"]


def test_a_defect_with_no_account_is_refused(shop):
    result = shop(
        "device", "inspect", "D-77", "--on", MORNING, "--by", "0142", "--defect", "wheels:"
    )
    assert isinstance(result.exception, ValidationError)


def test_a_device_with_nothing_on_record_says_so(shop):
    assert "nothing on record" in shop("device", "history", "D-77").output


def test_the_history_lists_both_kinds_and_the_next_due_date(shop):
    shop("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303")
    shop("device", "inspect", "D-77", "--on", MORNING, "--by", "0142")
    output = shop("device", "history", "D-77").output
    assert "quarterly" in output
    assert "pre use" in output
    assert "next quarterly due by 2025-09-02" in output


def test_a_load_with_both_checks_clears(shop):
    shop("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303")
    shop("device", "inspect", "D-77", "--on", MORNING, "--by", "0142")
    result = shop("device", "clear", "D-77", "--source", "IR-5512", "--on", MORNING)
    assert result.exit_code == 0
    assert "is clear for" in result.output


def test_a_load_that_is_not_cleared_exits_four_and_names_every_reason(shop):
    result = shop("device", "clear", "D-77", "--source", "IR-5512", "--on", MORNING)
    assert result.exit_code == 4
    assert "no quarterly inspection on record" in result.output
    assert "no pre use check that morning" in result.output


def test_a_source_nobody_registered_shows_up_in_the_reasons(shop):
    shop("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303")
    shop("device", "inspect", "D-77", "--on", MORNING, "--by", "0142")
    result = shop("device", "clear", "D-77", "--source", "IR-9999", "--on", MORNING)
    assert result.exit_code == 4
    assert "not on the inventory" in result.output
