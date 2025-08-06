"""The console script itself, which every other cli test goes around.

Every command test invokes the click group directly with standalone_mode off,
because that is how you assert on a domain error. That leaves the entry point
untested, and the entry point is the only thing a user actually runs. It is
also where the exit statuses are decided, and an exit status nobody checks is a
shell script that carries on after a failure.
"""

import subprocess
import sys

import click
import pytest

from qu_imager.cli.main import (
    EXIT_CONFIGURATION,
    EXIT_CONFLICT,
    EXIT_NOT_FOUND,
    EXIT_OK,
    EXIT_OTHER,
    EXIT_TECHNIQUE,
    EXIT_VALIDATION,
    exit_status,
    fail,
    run,
)
from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QualificationError,
    QuImagerError,
    TechniqueError,
    ValidationError,
)


def test_every_error_class_maps_to_its_own_status():
    assert exit_status(NotFoundError("weld", "GW-104")) == EXIT_NOT_FOUND
    assert exit_status(ConflictError("out")) == EXIT_CONFLICT
    assert exit_status(ValidationError("no")) == EXIT_VALIDATION
    assert exit_status(TechniqueError("no")) == EXIT_TECHNIQUE
    assert exit_status(ConfigurationError("no")) == EXIT_CONFIGURATION


def test_an_error_class_with_no_mapping_falls_back_rather_than_crashing():
    assert exit_status(QualificationError("no")) == EXIT_OTHER
    assert exit_status(QuImagerError("no")) == EXIT_OTHER


def test_failing_prints_the_code_and_leaves_with_its_status(capsys):
    with pytest.raises(SystemExit) as caught:
        fail(NotFoundError("weld", "GW-104"))
    assert caught.value.code == EXIT_NOT_FOUND
    assert "qi.not_found" in capsys.readouterr().err


def test_a_domain_error_from_a_command_becomes_its_exit_status(monkeypatch, capsys):
    def raiser(**kwargs):
        raise ConflictError("that source is already out", serial="IR-5512")

    monkeypatch.setattr("qu_imager.cli.main.cli.main", raiser)
    with pytest.raises(SystemExit) as caught:
        run()
    assert caught.value.code == EXIT_CONFLICT
    assert "IR-5512" in capsys.readouterr().err


def test_a_usage_error_prints_the_usage_and_exits_two(monkeypatch, capsys):
    def raiser(**kwargs):
        raise click.UsageError("no such option")

    monkeypatch.setattr("qu_imager.cli.main.cli.main", raiser)
    with pytest.raises(SystemExit) as caught:
        run()
    assert caught.value.code == EXIT_VALIDATION
    assert "no such option" in capsys.readouterr().err


def test_an_abort_says_so_rather_than_printing_a_traceback(monkeypatch, capsys):
    def raiser(**kwargs):
        raise click.exceptions.Abort()

    monkeypatch.setattr("qu_imager.cli.main.cli.main", raiser)
    with pytest.raises(SystemExit) as caught:
        run()
    assert caught.value.code == EXIT_OTHER
    assert "aborted" in capsys.readouterr().err


def test_a_command_that_worked_exits_zero(monkeypatch):
    monkeypatch.setattr("qu_imager.cli.main.cli.main", lambda **kwargs: None)
    with pytest.raises(SystemExit) as caught:
        run()
    assert caught.value.code == EXIT_OK


def test_loading_the_commands_registers_every_group():
    from qu_imager.cli.main import cli, load_commands

    load_commands()
    for name in (
        "job",
        "weld",
        "source",
        "device",
        "history",
        "ncr",
        "store",
        "morning",
        "report",
        "register",
        "technique",
    ):
        assert name in cli.commands


def a_module_run(*args):
    return subprocess.run(
        [sys.executable, "-m", "qu_imager.cli.main", *args],
        capture_output=True,
        text=True,
        check=False,
    )


def test_the_version_flag_prints_the_version():
    from click.testing import CliRunner

    from qu_imager import __version__
    from qu_imager.cli.main import cli

    result = CliRunner().invoke(cli, ["--version"])
    assert result.exit_code == 0
    assert __version__ in result.output


def test_the_help_lists_the_groups():
    from click.testing import CliRunner

    from qu_imager.cli.main import cli, load_commands

    load_commands()
    output = CliRunner().invoke(cli, ["--help"]).output
    assert "morning" in output
    assert "ncr" in output
