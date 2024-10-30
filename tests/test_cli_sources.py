import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ConflictError, NotFoundError

load_commands()

ASSAY = "2024-11-01T00:00:00"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    run("source", "add-device", "880D-221", "880d")
    return run


def register(run, serial="IR-5512", curies="90"):
    return run(
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


def test_a_source_is_registered(shop):
    result = register(shop)
    assert result.exit_code == 0
    assert result.output.strip() == "registered IR-5512"


def test_the_same_serial_twice_is_a_conflict(shop):
    register(shop)
    assert isinstance(register(shop).exception, ConflictError)


def test_an_isotope_nothing_knows_about_is_refused(shop):
    result = shop(
        "source", "register", "RA-1", "--isotope", "ra226", "--curies", "1", "--assayed", ASSAY
    )
    assert result.exception is not None


def test_the_sources_in_service_are_listed_with_where_they_are(shop):
    register(shop)
    listing = shop("source", "list").output
    assert "IR-5512" in listing
    assert "90.0 Ci" in listing
    assert "shop" in listing


def test_a_source_with_no_wipe_is_reported_unfit(shop):
    register(shop)
    result = shop("source", "fitness", "IR-5512", "--at", "2024-11-05T22:00:00")
    assert "IR-5512 fit: no" in result.output
    assert "leak test is not current" in result.output


def test_a_wiped_source_is_reported_fit(shop):
    register(shop)
    shop("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp")
    result = shop("source", "fitness", "IR-5512", "--at", "2024-11-05T22:00:00")
    assert "IR-5512 fit: yes" in result.output


def test_a_failing_wipe_says_the_source_is_out_of_service(shop):
    register(shop)
    result = shop("source", "wipe", "IR-5512", "--at", ASSAY, "--removed", "400", "--by", "rp")
    assert "failed" in result.output
    assert "awaiting disposal" in result.output


def test_a_source_goes_out_and_comes_back(shop):
    register(shop)
    out = shop(
        "source",
        "send-out",
        "IR-5512",
        "--device",
        "880D-221",
        "--crew",
        "0142",
        "--site",
        "Come By Chance",
        "--on",
        "2024-11-04",
    )
    assert "out to Come By Chance" in out.output
    assert "Come By Chance" in shop("source", "list").output
    back = shop("source", "bring-back", "IR-5512", "--on", "2024-11-06", "--exposures", "24")
    assert "back in the shop" in back.output


def test_a_source_cannot_go_out_twice(shop):
    register(shop)
    args = (
        "source",
        "send-out",
        "IR-5512",
        "--device",
        "880D-221",
        "--crew",
        "0142",
        "--site",
        "site",
        "--on",
        "2024-11-04",
    )
    shop(*args)
    assert isinstance(shop(*args).exception, ConflictError)


def test_a_source_that_is_not_there_is_not_found(shop):
    assert isinstance(shop("source", "fitness", "IR-9999").exception, NotFoundError)


def test_the_overdue_list_names_the_sources_that_need_wiping(shop):
    register(shop)
    listing = shop("source", "overdue", "--on", "2024-11-05").output
    assert "IR-5512" in listing
    shop("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp")
    assert "IR-5512" not in shop("source", "overdue", "--on", "2024-11-05").output


def test_the_history_reads_forwards_and_names_every_event(shop):
    register(shop)
    shop("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp")
    shop(
        "source",
        "send-out",
        "IR-5512",
        "--device",
        "880D-221",
        "--crew",
        "0142",
        "--site",
        "site",
        "--on",
        "2024-11-04",
    )
    lines = shop("source", "history", "IR-5512").output.splitlines()
    assert "assay" in lines[2]
    assert any("leak test" in line for line in lines)
    assert any("sent out" in line for line in lines)


def test_a_retired_source_drops_off_the_in_service_list(shop):
    register(shop)
    assert "awaiting disposal" in shop("source", "retire", "IR-5512").output
    assert "IR-5512" not in shop("source", "list").output


def test_a_device_goes_on_the_inventory_once(shop):
    assert isinstance(shop("source", "add-device", "880D-221", "880d").exception, ConflictError)
