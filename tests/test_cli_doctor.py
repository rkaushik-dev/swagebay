import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.services.doctor import check_names

load_commands()


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    run("init")
    return run


def test_a_clean_database_exits_zero_and_counts_the_checks(shop):
    result = shop("doctor")
    assert result.exit_code == 0
    assert f"{len(check_names())} checks, nothing found" in result.output


def test_a_finding_exits_four_and_names_the_check(shop):
    shop("device", "inspect", "D-99", "--on", "2025-08-12", "--by", "0142")
    result = shop("doctor")
    assert result.exit_code == 4
    assert "unknown device" in result.output
    assert "D-99" in result.output


def test_only_the_named_checks_are_run(shop):
    shop("device", "inspect", "D-99", "--on", "2025-08-12", "--by", "0142")
    result = shop("doctor", "--check", "self closed")
    assert result.exit_code == 0
    assert "1 checks, nothing found" in result.output


def test_a_check_nothing_knows_about_is_refused(shop):
    assert shop("doctor", "--check", "vibes").exit_code != 0


def test_more_than_one_check_can_be_named(shop):
    shop("device", "inspect", "D-99", "--on", "2025-08-12", "--by", "0142")
    result = shop("doctor", "--check", "self closed", "--check", "unknown device")
    assert result.exit_code == 4
    assert "2 checks, 1 findings" in result.output


def test_the_doctor_writes_nothing(shop, tmp_path):
    import hashlib
    import pathlib

    shop("device", "inspect", "D-99", "--on", "2025-08-12", "--by", "0142")
    path = pathlib.Path(tmp_path / "shop.sqlite3")
    before = hashlib.sha256(path.read_bytes()).hexdigest()
    shop("doctor")
    assert hashlib.sha256(path.read_bytes()).hexdigest() == before
