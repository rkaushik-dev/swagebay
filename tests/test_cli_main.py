import pytest
from click.testing import CliRunner

from qu_imager import __version__
from qu_imager.cli.main import (
    EXIT_CONFIGURATION,
    EXIT_CONFLICT,
    EXIT_NOT_FOUND,
    EXIT_OTHER,
    EXIT_TECHNIQUE,
    EXIT_VALIDATION,
    cli,
    exit_status,
)
from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QuImagerError,
    SafetyError,
    TechniqueError,
    ValidationError,
)


@pytest.fixture
def runner():
    return CliRunner()


def test_the_version_is_reported(runner):
    result = runner.invoke(cli, ["--version"], obj={})
    assert result.exit_code == 0
    assert __version__ in result.output


def test_the_help_names_what_the_tool_is(runner):
    result = runner.invoke(cli, ["--help"], obj={})
    assert result.exit_code == 0
    assert "technique planning" in result.output


def test_the_schema_is_built_into_a_named_database(runner, tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    result = runner.invoke(cli, ["--database", url, "init"], obj={})
    assert result.exit_code == 0
    assert "schema built" in result.output
    assert (tmp_path / "shop.sqlite3").exists()


def test_the_database_can_come_from_the_environment(runner, tmp_path, monkeypatch):
    url = f"sqlite:///{tmp_path / 'env.sqlite3'}"
    monkeypatch.setenv("QU_IMAGER_DATABASE", url)
    result = runner.invoke(cli, ["init"], obj={})
    assert result.exit_code == 0
    assert (tmp_path / "env.sqlite3").exists()


def test_each_class_of_failure_has_its_own_exit_status():
    assert exit_status(NotFoundError("source", "IR-1")) == EXIT_NOT_FOUND
    assert exit_status(ConflictError("busy")) == EXIT_CONFLICT
    assert exit_status(ValidationError("bad")) == EXIT_VALIDATION
    assert exit_status(TechniqueError("impossible")) == EXIT_TECHNIQUE
    assert exit_status(ConfigurationError("ours")) == EXIT_CONFIGURATION


def test_an_unclassified_failure_gets_the_general_status():
    assert exit_status(QuImagerError("something")) == EXIT_OTHER
    assert exit_status(SafetyError("over the limit")) == EXIT_OTHER


def test_the_statuses_are_all_different():
    statuses = {
        EXIT_VALIDATION,
        EXIT_NOT_FOUND,
        EXIT_CONFLICT,
        EXIT_TECHNIQUE,
        EXIT_CONFIGURATION,
        EXIT_OTHER,
    }
    assert len(statuses) == 6
    assert 0 not in statuses


def test_a_command_that_does_not_exist_is_a_usage_error(runner):
    result = runner.invoke(cli, ["not-a-command"], obj={})
    assert result.exit_code != 0


def test_the_more_specific_status_wins():
    assert exit_status(NotFoundError("weld", "GW-1")) != exit_status(ValidationError("x"))
