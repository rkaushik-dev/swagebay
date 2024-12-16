import csv
import io

import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import NotFoundError

load_commands()

OPENED = "2024-12-01"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    run(
        "job",
        "open",
        "--client",
        "Maritime Pipelines",
        "--site",
        "Come By Chance",
        "--procedure",
        "RT-001",
        "--on",
        OPENED,
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
        OPENED,
    )
    return run


def test_the_package_prints_the_faults_before_the_summary(shop):
    output = shop("report", "package", "24-0001").output
    lines = output.splitlines()
    faults = next(i for i, line in enumerate(lines) if "no radiographs" in line)
    summary = next(i for i, line in enumerate(lines) if line.startswith("client"))
    assert faults < summary


def test_the_package_carries_the_job_details(shop):
    output = shop("report", "package", "24-0001").output
    assert "report package 24-0001" in output
    assert "client          Maritime Pipelines" in output
    assert "complete        no" in output


def test_the_repair_rate_is_printed_as_a_percentage(shop):
    assert "repair rate     0.00%" in shop("report", "package", "24-0001").output


def test_the_weld_table_lists_every_weld(shop):
    lines = shop("report", "welds", "24-0001").output.splitlines()
    assert lines[0].split()[:3] == ["weld", "wall", "welder"]
    assert lines[2].startswith("GW-104")


def test_a_weld_with_no_films_has_no_disposition(shop):
    assert "none" in shop("report", "welds", "24-0001").output


def test_the_traceability_command_says_so_when_nothing_is_broken(shop):
    assert shop("report", "traceability", "24-0001").output.strip() == "every chain holds"


def test_the_export_writes_three_files_and_names_them(tmp_path, shop):
    into = tmp_path / "out"
    output = shop("report", "export", "24-0001", "--into", str(into)).output
    written = [line.strip() for line in output.splitlines() if line.strip()]
    assert len(written) == 3
    for name in written:
        assert name.endswith(".csv")
    assert (into / "24-0001-summary.csv").exists()


def test_the_export_makes_the_directory_if_it_is_not_there(tmp_path, shop):
    into = tmp_path / "deep" / "nested"
    shop("report", "export", "24-0001", "--into", str(into))
    assert into.is_dir()


def test_one_sheet_can_be_printed_to_standard_output(shop):
    text = shop("report", "sheet", "24-0001", "summary").output
    rows = list(csv.reader(io.StringIO(text)))
    assert rows[0][0] == "job"
    assert rows[1][0] == "24-0001"


def test_a_sheet_nobody_publishes_is_refused_by_the_choice(shop):
    result = shop("report", "sheet", "24-0001", "everything")
    assert result.exit_code != 0


def test_a_job_that_is_not_there_is_not_found(shop):
    assert isinstance(shop("report", "package", "24-0099").exception, NotFoundError)


def test_the_exported_csv_has_no_carriage_returns_in_it(tmp_path, shop):
    into = tmp_path / "out"
    shop("report", "export", "24-0001", "--into", str(into))
    raw = (into / "24-0001-welds.csv").read_bytes()
    assert b"\r" not in raw
