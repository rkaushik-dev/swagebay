import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ConflictError, NotFoundError

load_commands()

FOUND = "2025-04-07T23:30:00"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    return run


def incident(run, reference="INC-1"):
    return run(
        "register",
        "incident",
        reference,
        "--kind",
        "OVEREXPOSURE",
        "--found",
        FOUND,
        "--site",
        "Come By Chance",
        "--by",
        "0142",
        "--account",
        "the source hung up",
    )


def receive(run, lot="L-1", sheets=100, expires="2026-04-07"):
    return run(
        "register",
        "receive",
        lot,
        "--sheets",
        str(sheets),
        "--received",
        "2025-04-01",
        "--expires",
        expires,
    )


def test_an_incident_is_recorded_and_prints_when_it_is_due(shop):
    result = incident(shop)
    assert result.exit_code == 0
    assert "INC-1" in result.output
    assert "due 2025-04-08 23:30" in result.output


def test_the_same_reference_twice_is_a_conflict(shop):
    incident(shop)
    assert isinstance(incident(shop).exception, ConflictError)


def test_an_occurrence_nobody_publishes_is_refused(shop):
    result = shop(
        "register",
        "incident",
        "INC-2",
        "--kind",
        "SOMETHING_ELSE",
        "--found",
        FOUND,
        "--site",
        "s",
        "--by",
        "0142",
        "--account",
        "a",
    )
    assert result.exception is not None


def test_the_outstanding_list_names_what_is_unreported(shop):
    incident(shop)
    assert "INC-1" in shop("register", "outstanding").output


def test_reporting_it_empties_the_outstanding_list(shop):
    incident(shop)
    assert "reported" in shop("register", "notify", "INC-1", "--at", FOUND).output
    assert shop("register", "outstanding").output.strip() == ""


def test_reporting_something_that_is_not_there_is_not_found(shop):
    assert isinstance(shop("register", "notify", "INC-9", "--at", FOUND).exception, NotFoundError)


def test_a_calibration_is_recorded_and_the_instrument_stops_being_expired(shop):
    result = shop(
        "register",
        "calibrate",
        "LU-1",
        "--on",
        "2025-04-01",
        "--due",
        "2026-04-01",
    )
    assert "calibrated until 2026-04-01" in result.output
    assert shop("register", "expired", "--on", "2025-05-01").output.strip() == ""
    assert "LU-1" in shop("register", "expired", "--on", "2026-05-01").output


def test_the_same_calibration_twice_is_a_conflict(shop):
    args = ("register", "calibrate", "LU-1", "--on", "2025-04-01", "--due", "2026-04-01")
    shop(*args)
    assert isinstance(shop(*args).exception, ConflictError)


def test_film_is_received_and_listed(shop):
    assert "received L-1, 100 sheets" in receive(shop).output
    listing = shop("register", "stock", "--on", "2025-04-07").output
    assert "L-1" in listing
    assert "100" in listing


def test_the_stock_list_comes_back_shortest_dated_first(shop):
    receive(shop, "L-1", expires="2026-06-01")
    receive(shop, "L-2", expires="2025-06-01")
    lines = shop("register", "stock", "--on", "2025-04-07").output.splitlines()
    assert lines[2].startswith("L-2")


def test_issuing_film_names_the_lots_and_what_is_left(shop):
    receive(shop, "L-1", sheets=60, expires="2025-06-01")
    receive(shop, "L-2", sheets=100, expires="2026-06-01")
    output = shop("register", "issue", "--sheets", "80", "--on", "2025-04-07").output
    assert "L-1 60" in output
    assert "L-2 20" in output
    assert "80 sheets left" in output


def test_asking_for_more_than_there_is_is_a_conflict(shop):
    receive(shop, sheets=20)
    result = shop("register", "issue", "--sheets", "80", "--on", "2025-04-07")
    assert isinstance(result.exception, ConflictError)


def test_an_expired_lot_is_called_out_on_the_stock_list(shop):
    receive(shop, "L-1", expires="2025-04-05")
    output = shop("register", "stock", "--on", "2025-04-07").output
    assert "expired: L-1" in output


def test_an_empty_register_prints_nothing_at_all(shop):
    assert shop("register", "outstanding").output.strip() == ""
    assert shop("register", "expired", "--on", "2025-04-07").output.strip() == ""
    assert shop("register", "stock", "--on", "2025-04-07").output.strip() == ""
