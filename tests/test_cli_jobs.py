import pytest
from click.testing import CliRunner

from qu_imager.cli.main import EXIT_CONFLICT, EXIT_NOT_FOUND, EXIT_VALIDATION, cli, load_commands

load_commands()


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    return run


def open_job(run, client="Maritime Pipelines"):
    return run(
        "job",
        "open",
        "--client",
        client,
        "--site",
        "Come By Chance",
        "--procedure",
        "RT-001",
        "--on",
        "2024-10-30",
    )


def add_weld(run, weld="GW-104"):
    return run(
        "weld",
        "add",
        "24-0001",
        weld,
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
        "2024-10-30",
    )


def test_a_job_is_opened_and_its_number_printed(shop):
    result = open_job(shop)
    assert result.exit_code == 0
    assert result.output.strip() == "opened 24-0001"


def test_the_numbers_run_on(shop):
    open_job(shop)
    assert open_job(shop, "Other").output.strip() == "opened 24-0002"


def test_an_acceptance_code_nothing_knows_about_is_refused(shop):
    result = shop(
        "job",
        "open",
        "--client",
        "c",
        "--site",
        "s",
        "--procedure",
        "RT-001",
        "--code",
        "BS 2600",
        "--on",
        "2024-10-30",
    )
    assert result.exception is not None


def test_the_open_jobs_are_listed_as_a_table(shop):
    open_job(shop)
    result = shop("job", "list", "--on", "2024-11-01")
    lines = result.output.splitlines()
    assert lines[0].split() == ["job", "client", "site", "code", "welds"]
    assert "24-0001" in lines[2]


def test_a_job_is_shown_with_what_is_outstanding(shop):
    open_job(shop)
    result = shop("job", "show", "24-0001")
    assert "job        24-0001" in result.output
    assert "clear      no" in result.output
    assert "outstanding: the job has no welds on it" in result.output


def test_a_job_that_is_not_there_raises_not_found(shop):
    result = shop("job", "show", "24-0099")
    assert result.exception is not None
    from qu_imager.errors import NotFoundError

    assert isinstance(result.exception, NotFoundError)


def test_a_weld_is_added_and_listed(shop):
    open_job(shop)
    assert add_weld(shop).output.strip() == "added GW-104 to 24-0001"
    listing = shop("weld", "list", "24-0001").output
    assert "GW-104" in listing
    assert "10.97 mm" in listing
    assert "168.30 mm" in listing


def test_a_plate_weld_prints_plate_where_the_diameter_would_be(shop):
    open_job(shop)
    shop(
        "weld",
        "add",
        "24-0001",
        "GW-200",
        "--wall",
        "12 mm",
        "--length",
        "800 mm",
        "--welder",
        "w-19",
        "--on",
        "2024-10-30",
    )
    assert "plate" in shop("weld", "list", "24-0001").output


def test_the_same_weld_twice_is_a_conflict(shop):
    open_job(shop)
    add_weld(shop)
    result = add_weld(shop)
    from qu_imager.errors import ConflictError

    assert isinstance(result.exception, ConflictError)


def test_a_wall_that_will_not_parse_is_refused(shop):
    open_job(shop)
    result = shop(
        "weld",
        "add",
        "24-0001",
        "GW-104",
        "--wall",
        "ten mm",
        "--length",
        "529 mm",
        "--welder",
        "w-19",
    )
    from qu_imager.errors import ValidationError

    assert isinstance(result.exception, ValidationError)


def test_the_weld_count_on_the_job_follows_what_was_added(shop):
    open_job(shop)
    add_weld(shop)
    assert "welds      1" in shop("job", "show", "24-0001").output


def test_a_job_with_nothing_done_refuses_to_close(shop):
    open_job(shop)
    result = shop("job", "close", "24-0001", "--on", "2024-11-05")
    from qu_imager.errors import ConflictError

    assert isinstance(result.exception, ConflictError)


def test_the_exit_statuses_are_the_ones_the_module_publishes():
    assert EXIT_VALIDATION != EXIT_NOT_FOUND != EXIT_CONFLICT
