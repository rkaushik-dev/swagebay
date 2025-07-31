"""What has to be true of the package itself, checked from inside the tests.

Two of these have been wrong at some point in a project and neither shows up in
any other test. A package with no type marker type checks perfectly in its own
repository and is untyped everywhere else. A console script whose target moved
imports fine and fails on the first line the user types.

The version check is here rather than in a release script because a version
that does not match the metadata is a version that will be wrong in a bug
report.
"""

from importlib import metadata

import qu_imager


def test_the_type_marker_is_beside_the_package():
    import pathlib

    marker = pathlib.Path(qu_imager.__file__).parent / "py.typed"
    assert marker.exists()


def test_the_type_marker_is_declared_as_package_data():
    import pathlib
    import tomllib

    root = pathlib.Path(qu_imager.__file__).parent.parent.parent
    config = tomllib.loads((root / "pyproject.toml").read_text())
    assert config["tool"]["setuptools"]["package-data"]["qu_imager"] == ["py.typed"]


def test_the_console_script_points_at_something_that_exists():
    import pathlib
    import tomllib

    root = pathlib.Path(qu_imager.__file__).parent.parent.parent
    config = tomllib.loads((root / "pyproject.toml").read_text())
    target = config["project"]["scripts"]["qu-imager"]
    module_name, _, attribute = target.partition(":")
    module = __import__(module_name, fromlist=[attribute])
    assert callable(getattr(module, attribute))


def test_the_declared_version_matches_the_package():
    import pathlib
    import tomllib

    root = pathlib.Path(qu_imager.__file__).parent.parent.parent
    config = tomllib.loads((root / "pyproject.toml").read_text())
    assert config["project"]["version"] == qu_imager.__version__


def test_every_runtime_dependency_is_pinned_exactly():
    import pathlib
    import tomllib

    root = pathlib.Path(qu_imager.__file__).parent.parent.parent
    config = tomllib.loads((root / "pyproject.toml").read_text())
    for requirement in config["project"]["dependencies"]:
        assert "==" in requirement, requirement


def test_the_dev_dependencies_are_pinned_too():
    import pathlib
    import tomllib

    root = pathlib.Path(qu_imager.__file__).parent.parent.parent
    config = tomllib.loads((root / "pyproject.toml").read_text())
    for requirement in config["project"]["optional-dependencies"]["dev"]:
        assert "==" in requirement, requirement


def test_the_package_imports_without_a_database_or_a_config_file():
    import os
    import pathlib
    import subprocess
    import sys

    environment = dict(os.environ)
    environment["PYTHONPATH"] = str(pathlib.Path(qu_imager.__file__).parent.parent)
    result = subprocess.run(
        [sys.executable, "-c", "import qu_imager; print(qu_imager.__version__)"],
        capture_output=True,
        text=True,
        check=False,
        env=environment,
    )
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == qu_imager.__version__


def test_the_distribution_metadata_agrees_when_it_is_installed():
    try:
        installed = metadata.version("qu-imager")
    except metadata.PackageNotFoundError:
        return
    assert installed == qu_imager.__version__
