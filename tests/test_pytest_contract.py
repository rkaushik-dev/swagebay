"""What the test run has to print, which is not the same as what it has to do.

Anything that reads this suite from the outside reads its output. A verifier, a
CI annotator, a flaky test tracker: all of them key on the per case lines pytest
writes under -v, `tests/test_thing.py::test_name PASSED`, and none of them can
tell the difference between a run that printed no such lines and a run that
found no such tests. Both look like an empty list.

That happened here. `addopts = "-q"` sat in pyproject for most of this project's
life. pytest adds addopts to the front of the command line and works out
verbosity by counting, so -q followed by the caller's -v came out at zero and
`pytest -v` printed a row of dots. Every test passed the whole time, which is
exactly why nobody noticed.

So the contract is tested from the outside, by running pytest in a subprocess
and reading what it actually wrote. Asserting on the config file alone would
have passed on the day the trap was introduced, because the config was doing
precisely what it said.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import tomllib

import qu_imager

PER_CASE = re.compile(r"^(?P<path>\S+\.py)::(?P<name>\w+)\s+(?P<outcome>PASSED|FAILED)")
SAMPLE = "tests/test_naming.py"


def root() -> pathlib.Path:
    return pathlib.Path(qu_imager.__file__).parent.parent.parent


def run_pytest(*args: str) -> subprocess.CompletedProcess[str]:
    """Run the suite the way something outside this repository would."""
    return subprocess.run(
        [sys.executable, "-m", "pytest", *args],
        capture_output=True,
        text=True,
        check=False,
        cwd=root(),
    )


def per_case_lines(output: str) -> list[re.Match[str]]:
    found = (PER_CASE.match(line.strip()) for line in output.splitlines())
    return [match for match in found if match is not None]


def test_dash_v_prints_a_line_for_every_case():
    result = run_pytest("-v", SAMPLE)
    assert result.returncode == 0, result.stdout[-2000:]
    assert len(per_case_lines(result.stdout)) > 10


def test_the_ids_are_fully_qualified_because_that_is_what_gets_keyed_on():
    matches = per_case_lines(run_pytest("-v", SAMPLE).stdout)
    assert matches, "no per case lines at all"
    for match in matches:
        assert match.group("path").endswith(".py")
        assert match.group("name").startswith("test_")


def test_the_ids_match_what_collect_only_says_the_suite_holds():
    collected = run_pytest("--collect-only", "-q", SAMPLE).stdout
    names = {line.split("::")[-1].strip() for line in collected.splitlines() if "::" in line}
    reported = {match.group("name") for match in per_case_lines(run_pytest("-v", SAMPLE).stdout)}
    assert names
    assert names == reported


def test_dash_q_still_suppresses_them_when_it_is_asked_for():
    assert per_case_lines(run_pytest("-q", SAMPLE).stdout) == []


def test_the_default_run_is_not_forced_quiet():
    """Quiet has to be the caller's decision, or -v cannot undo it."""
    assert run_pytest("-v", SAMPLE).stdout != run_pytest("-q", SAMPLE).stdout


def test_the_config_declares_no_verbosity_option():
    config = tomllib.loads((root() / "pyproject.toml").read_text())
    options = config["tool"]["pytest"]["ini_options"]
    declared = options.get("addopts", "")
    assert "-q" not in declared
    assert "-v" not in declared
    assert "--quiet" not in declared


def test_the_settings_that_do_belong_in_the_config_are_still_there():
    config = tomllib.loads((root() / "pyproject.toml").read_text())
    options = config["tool"]["pytest"]["ini_options"]
    assert options["testpaths"] == ["tests"]
    assert options["pythonpath"] == ["src", "tests"]
    assert options["filterwarnings"]


def test_ci_asks_for_quiet_rather_than_relying_on_the_config():
    workflow = (root() / ".github/workflows/ci.yml").read_text()
    lines = [line for line in workflow.splitlines() if "python -m pytest" in line]
    assert lines
    for line in lines:
        assert " -q " in line, line
