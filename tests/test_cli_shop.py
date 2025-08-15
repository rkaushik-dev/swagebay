import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ConfigurationError, ConflictError, NotFoundError

load_commands()

ASSAY = "2025-06-01T00:00:00"
SWAP = "2025-08-14"
DAY = "2025-08-18"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    run("source", "add-device", "D-77", "880d")
    run("source", "add-device", "D-99", "tk100")
    for serial, curies in (("IR-5512", "90"), ("IR-6000", "80")):
        run(
            "source",
            "register",
            serial,
            "--isotope",
            "ir192",
            "--curies",
            curies,
            "--assayed",
            ASSAY,
        )
        run("source", "wipe", serial, "--at", ASSAY, "--by", "rp")
    return run


def load(shop, device="D-77", installed="IR-5512", removed=None, on=SWAP):
    args = ["shop", "exchange", device, "--shipment", "QSA-1", "--on", on, "--by", "0142"]
    if installed:
        args += ["--in", installed]
    if removed:
        args += ["--out", removed]
    return shop(*args)


def test_a_load_is_recorded_and_reads_back(shop):
    result = load(shop)
    assert result.exit_code == 0
    assert "IR-5512 into D-77" in result.output
    assert "IR-5512" in shop("shop", "loading").output


def test_a_device_holding_nothing_says_so(shop):
    assert "no device is holding a source" in shop("shop", "loading").output


def test_a_second_load_without_an_unload_is_refused(shop):
    load(shop)
    assert isinstance(load(shop, installed="IR-6000").exception, ConflictError)


def test_a_source_already_in_another_device_is_refused(shop):
    load(shop)
    result = load(shop, device="D-99", installed="IR-5512")
    assert isinstance(result.exception, ConflictError)


def test_the_wrong_isotope_for_the_device_is_refused(shop):
    result = load(shop, device="D-99", installed="IR-5512")
    assert isinstance(result.exception, ConfigurationError)


def test_a_device_nobody_registered_is_not_found(shop):
    assert isinstance(load(shop, device="D-11").exception, NotFoundError)


def test_a_swap_leaves_the_new_source_in(shop):
    load(shop)
    shop("source", "wipe", "IR-6000", "--at", "2025-12-01T00:00:00", "--by", "rp")
    result = load(shop, removed="IR-5512", installed="IR-6000", on="2026-01-10")
    assert result.exit_code == 0
    output = shop("shop", "loading").output
    assert "IR-6000" in output
    assert "IR-5512" not in output


def test_the_source_that_came_out_is_in_transit(shop):
    load(shop)
    load(shop, removed="IR-5512", installed=None, on="2026-01-10")
    assert "IR-5512" not in shop("source", "list").output


def test_a_days_use_is_logged(shop):
    load(shop)
    result = shop(
        "shop",
        "use",
        "D-77",
        "--source",
        "IR-5512",
        "--site",
        "Come By Chance",
        "--on",
        DAY,
        "--by",
        "0142",
        "--exposures",
        "14",
    )
    assert result.exit_code == 0
    assert "14 exposures" in result.output


def test_a_use_naming_a_source_the_device_was_not_holding_is_refused(shop):
    load(shop)
    result = shop(
        "shop",
        "use",
        "D-77",
        "--source",
        "IR-6000",
        "--site",
        "site",
        "--on",
        DAY,
        "--by",
        "0142",
    )
    assert isinstance(result.exception, ConflictError)


def test_a_use_with_nobody_on_it_is_refused_by_click(shop):
    result = shop("shop", "use", "D-77", "--source", "IR-5512", "--site", "site", "--on", DAY)
    assert result.exit_code != 0


def test_exposures_can_be_topped_up(shop):
    load(shop)
    shop(
        "shop",
        "use",
        "D-77",
        "--source",
        "IR-5512",
        "--site",
        "Come By Chance",
        "--on",
        DAY,
        "--by",
        "0142",
        "--exposures",
        "14",
    )
    result = shop(
        "shop", "exposures", "D-77", "--site", "Come By Chance", "--on", DAY, "--add", "6"
    )
    assert "20 exposures" in result.output


def test_a_window_with_no_trips_has_no_gaps(shop):
    result = shop("shop", "gaps", "--since", "2025-08-01", "--until", "2025-08-31")
    assert result.exit_code == 0
    assert "nothing missing" in result.output


def test_a_window_that_ends_before_it_starts_is_refused(shop):
    result = shop("shop", "gaps", "--since", "2025-08-31", "--until", "2025-08-01")
    assert result.exception is not None


def test_a_trip_with_nothing_logged_shows_up_as_a_gap(shop):
    load(shop)
    shop("device", "inspect", "D-77", "--quarterly", "--on", "2025-08-01", "--by", "0303")
    shop("device", "inspect", "D-77", "--on", DAY, "--by", "0142")
    shop(
        "source",
        "send-out",
        "IR-5512",
        "--device",
        "D-77",
        "--crew",
        "0142",
        "--site",
        "Come By Chance",
        "--on",
        DAY,
    )
    shop("source", "bring-back", "IR-5512", "--on", "2025-08-19", "--exposures", "14")
    result = shop("shop", "gaps", "--since", "2025-08-01", "--until", "2025-08-31")
    assert result.exit_code == 4
    assert "D-77 on 2025-08-18" in result.output
    assert "D-77 on 2025-08-19" in result.output
