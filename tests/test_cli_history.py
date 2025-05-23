from datetime import datetime

import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.persistence.unit_of_work import (
    build_engine,
    transaction,
    unit_of_work_factory,
)

load_commands()

WHEN = datetime(2025, 5, 22, 8, 30)
SIGNED = datetime(2025, 5, 22, 16, 0)
AFTER = datetime(2025, 5, 23, 9, 0)
FILM = "24-0001-R0001"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"

    def run(*args):
        return CliRunner().invoke(cli, ["--database", url, *args], obj={})

    def seed():
        return transaction(unit_of_work_factory(build_engine(url)))

    run("init")
    run.seed = seed
    return run


def read_and_reread(shop):
    with shop.seed() as work:
        work.audit.created(WHEN, "0142", "radiograph", FILM)
        work.audit.changed(
            WHEN, "0142", "radiograph", FILM, "indications", "nothing", "ESI 20.0 mm", "second pass"
        )


def test_a_record_with_no_history_says_so(shop):
    result = shop("history", "record", "radiograph", FILM)
    assert result.exit_code == 0
    assert "nothing recorded" in result.output


def test_one_records_history_prints_the_old_value(shop):
    read_and_reread(shop)
    result = shop("history", "record", "radiograph", FILM)
    assert result.exit_code == 0
    assert "ESI 20.0 mm" in result.output
    assert "second pass" in result.output
    assert "created" in result.output


def test_a_record_kind_nothing_knows_about_is_refused(shop):
    assert shop("history", "record", "gizmo", FILM).exit_code != 0


def test_one_badges_day_prints_without_the_who_column(shop):
    read_and_reread(shop)
    result = shop("history", "actor", "0142", "--on", "2025-05-22")
    assert result.exit_code == 0
    assert "who" not in result.output
    assert "ESI 20.0 mm" in result.output


def test_a_badge_that_changed_nothing_says_so(shop):
    read_and_reread(shop)
    assert "changed nothing" in shop("history", "actor", "0303", "--on", "2025-05-22").output


def test_a_day_the_badge_was_not_working_is_empty(shop):
    read_and_reread(shop)
    assert "changed nothing" in shop("history", "actor", "0142", "--on", "2025-06-01").output


def test_more_than_one_day_can_be_asked_for(shop):
    read_and_reread(shop)
    result = shop("history", "actor", "0142", "--on", "2025-05-20", "--days", "5")
    assert "ESI 20.0 mm" in result.output


def test_zero_days_is_refused(shop):
    result = shop("history", "actor", "0142", "--on", "2025-05-22", "--days", "0")
    assert result.exit_code != 0


def test_nothing_after_a_signature_nobody_gave(shop):
    read_and_reread(shop)
    result = shop("history", "after-signing", "radiograph", FILM)
    assert result.exit_code == 0
    assert "nothing changed" in result.output


def test_a_change_after_signing_exits_four(shop):
    read_and_reread(shop)
    with shop.seed() as work:
        work.audit.signed(SIGNED, "0303", "radiograph", FILM)
        work.audit.changed(
            AFTER, "0201", "radiograph", FILM, "indications", "ESI 20.0 mm", "nothing"
        )
    result = shop("history", "after-signing", "radiograph", FILM)
    assert result.exit_code == 4
    assert "changed after signing" in result.output


def test_the_signature_itself_is_not_a_change_after_it(shop):
    with shop.seed() as work:
        work.audit.signed(SIGNED, "0303", "radiograph", FILM)
    result = shop("history", "after-signing", "radiograph", FILM)
    assert result.exit_code == 0
