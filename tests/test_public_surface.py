"""The names a consumer imports, and that they keep meaning the same thing.

A package with a front door has to have a test for it, because the front door
is the only part of it somebody else's code depends on by name. Moving a module
is free until it is not, and the moment it is not is the moment a consumer
upgrades.

The version check is here as well as in the packaging test. That one asserts
the metadata agrees with the package; this one asserts the package publishes
it at all, which is what a bug report quotes.
"""

import qu_imager


def test_the_version_is_published_and_looks_like_one():
    parts = qu_imager.__version__.split(".")
    assert len(parts) == 3
    assert all(part.isdigit() for part in parts)


def test_the_version_is_in_the_public_names():
    assert "__version__" in qu_imager.__all__


def test_every_published_name_is_actually_there():
    for name in qu_imager.__all__:
        assert hasattr(qu_imager, name), name


def test_the_published_names_are_sorted_so_a_diff_stays_readable():
    assert list(qu_imager.__all__) == sorted(qu_imager.__all__)


def test_the_quantity_constructors_are_at_the_front_door():
    assert qu_imager.mm(10).mm == 10
    assert qu_imager.inch(1) == qu_imager.mm("25.4")
    assert qu_imager.curie(1).bq == 37_000_000_000
    assert qu_imager.seconds(90) == qu_imager.minutes("1.5")
    assert qu_imager.usv(1000).msv == 1


def test_the_error_hierarchy_is_at_the_front_door():
    assert issubclass(qu_imager.ValidationError, qu_imager.QuImagerError)
    assert issubclass(qu_imager.ConflictError, qu_imager.QuImagerError)
    assert issubclass(qu_imager.NotFoundError, qu_imager.QuImagerError)
    assert issubclass(qu_imager.ValidationError, ValueError)


def test_every_published_error_carries_a_stable_code():
    codes = set()
    for name in qu_imager.__all__:
        thing = getattr(qu_imager, name)
        if isinstance(thing, type) and issubclass(thing, qu_imager.QuImagerError):
            assert thing.code.startswith("qi.")
            codes.add(thing.code)
    assert len(codes) >= 7


def test_the_identities_are_at_the_front_door():
    assert str(qu_imager.JobNumber.parse("24-0001")) == "24-0001"
    assert str(qu_imager.WeldId.parse("GW-104R1")) == "GW-104R1"
    assert str(qu_imager.ClockPosition.parse("3:00")) == "3:00"


def test_the_front_door_re_exports_rather_than_redefining():
    from qu_imager.quantities.length import Length

    assert qu_imager.Length is Length


def test_importing_the_front_door_does_not_pull_in_the_database():
    import subprocess
    import sys
    from pathlib import Path

    script = (
        "import sys, qu_imager; print(any(name.startswith('sqlalchemy') for name in sys.modules))"
    )
    result = subprocess.run(
        [sys.executable, "-c", script],
        capture_output=True,
        text=True,
        check=False,
        env={"PYTHONPATH": str(Path(qu_imager.__file__).parent.parent), "PATH": ""},
    )
    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "False"
