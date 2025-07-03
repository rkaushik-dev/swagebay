import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ConflictError

load_commands()

RAISED = "2025-07-02"
LATER = "2025-07-08"
MUCH_LATER = "2025-09-01"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    return run


def raised(shop, reference="NCR-1", job="24-0001", by="0142"):
    return shop(
        "ncr",
        "raise",
        reference,
        "--job",
        job,
        "--item",
        "GW-104",
        "--requirement",
        "API 1104 9.3.8",
        "--account",
        "elongated slag 32 mm",
        "--on",
        RAISED,
        "--by",
        by,
    )


def test_a_report_is_raised_undecided(shop):
    result = raised(shop)
    assert result.exit_code == 0
    assert "undecided, open" in result.output


def test_the_same_reference_twice_is_a_conflict(shop):
    raised(shop)
    assert isinstance(raised(shop).exception, ConflictError)


def test_rework_needs_nothing_else(shop):
    raised(shop)
    result = shop("ncr", "decide", "NCR-1", "rework")
    assert "rework, open" in result.output


def test_use_as_is_without_a_concession_is_refused(shop):
    raised(shop)
    assert isinstance(shop("ncr", "decide", "NCR-1", "use_as_is").exception, ConflictError)


def test_use_as_is_with_a_concession_goes_through(shop):
    raised(shop)
    result = shop("ncr", "decide", "NCR-1", "use_as_is", "--concession", "CON-88")
    assert "use as is" in result.output


def test_a_disposition_nothing_knows_about_is_refused(shop):
    raised(shop)
    assert shop("ncr", "decide", "NCR-1", "have_a_think").exit_code != 0


def test_closing_needs_a_decision_first(shop):
    raised(shop)
    result = shop("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303")
    assert isinstance(result.exception, ConflictError)


def test_the_author_cannot_close_their_own(shop):
    raised(shop)
    shop("ncr", "decide", "NCR-1", "rework")
    result = shop("ncr", "close", "NCR-1", "--on", LATER, "--by", "0142")
    assert isinstance(result.exception, ConflictError)


def test_somebody_else_can(shop):
    raised(shop)
    shop("ncr", "decide", "NCR-1", "rework")
    result = shop("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303")
    assert "closed 2025-07-08 by 0303" in result.output


def test_nothing_open_says_so(shop):
    assert "nothing open" in shop("ncr", "open").output


def test_the_open_list_shows_the_report(shop):
    raised(shop)
    output = shop("ncr", "open", "--on", LATER).output
    assert "NCR-1" in output
    assert "undecided" in output
    assert "overdue" not in output


def test_a_report_left_two_months_shows_as_overdue(shop):
    raised(shop)
    output = shop("ncr", "open", "--on", MUCH_LATER).output
    assert "overdue" in output


def test_a_closed_report_drops_off_the_open_list(shop):
    raised(shop)
    shop("ncr", "decide", "NCR-1", "rework")
    shop("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303")
    assert "nothing open" in shop("ncr", "open").output


def test_a_job_with_nothing_raised_says_so(shop):
    assert "nothing raised against" in shop("ncr", "job", "24-0001").output


def test_an_open_report_blocks_the_job_and_exits_four(shop):
    raised(shop)
    result = shop("ncr", "job", "24-0001", "--on", LATER)
    assert result.exit_code == 4
    assert "stopping the sign off" in result.output


def test_a_job_with_everything_closed_does_not_block(shop):
    raised(shop)
    shop("ncr", "decide", "NCR-1", "rework")
    shop("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303")
    result = shop("ncr", "job", "24-0001", "--on", LATER)
    assert result.exit_code == 0
    assert "stopping the sign off" not in result.output


def test_another_jobs_report_is_not_on_this_list(shop):
    raised(shop, "NCR-2", job="24-0002")
    assert "nothing raised against" in shop("ncr", "job", "24-0001").output
