"""The migrations and the models have to agree, and the check has to be run.

A schema built by create_all and a schema built by the migrations are two
different artifacts, and they drift the moment somebody adds a column to a
model and forgets the revision. The only way to catch that is to build both and
compare, which is what this does.

Comparing is done with alembic's own autogenerate machinery rather than by
listing columns by hand, because autogenerate knows about the things a hand
written comparison misses: server defaults, constraint names, nullability, and
the difference between a type that is equal and a type that is the same.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from alembic.script import ScriptDirectory
from sqlalchemy import inspect

from qu_imager.persistence.base import Base
from qu_imager.persistence.unit_of_work import build_engine, create_schema

ROOT = Path(__file__).resolve().parents[1]


def alembic_config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.set_main_option("sqlalchemy.url", url)
    return config


def upgraded(tmp_path) -> object:
    from alembic import command

    url = f"sqlite:///{tmp_path / 'migrated.sqlite3'}"
    command.upgrade(alembic_config(url), "head")
    return build_engine(url)


def test_there_is_exactly_one_head():
    scripts = ScriptDirectory(str(ROOT / "migrations"))
    assert len(scripts.get_heads()) == 1


def test_every_revision_has_a_downgrade():
    scripts = ScriptDirectory(str(ROOT / "migrations"))
    for revision in scripts.walk_revisions():
        source = Path(revision.path).read_text(encoding="utf-8")
        assert "def downgrade()" in source
        assert source.count("pass") <= 1


def test_the_migrations_build_every_table_the_models_declare(tmp_path):
    engine = upgraded(tmp_path)
    built = set(inspect(engine).get_table_names()) - {"alembic_version"}
    assert built == set(Base.metadata.tables)


def test_the_migrated_schema_matches_the_models(tmp_path):
    engine = upgraded(tmp_path)
    with engine.connect() as connection:
        context = MigrationContext.configure(connection)
        differences = compare_metadata(context, Base.metadata)
    assert differences == []


def test_the_migrations_come_apart_again(tmp_path):
    from alembic import command

    url = f"sqlite:///{tmp_path / 'roundtrip.sqlite3'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    engine = build_engine(url)
    left = set(inspect(engine).get_table_names()) - {"alembic_version"}
    assert left == set()


def test_upgrade_and_downgrade_can_be_repeated(tmp_path):
    from alembic import command

    url = f"sqlite:///{tmp_path / 'twice.sqlite3'}"
    config = alembic_config(url)
    for _ in range(2):
        command.upgrade(config, "head")
        command.downgrade(config, "base")
    engine = build_engine(url)
    assert set(inspect(engine).get_table_names()) - {"alembic_version"} == set()


def test_create_all_and_the_migrations_agree_on_the_table_list(tmp_path):
    direct = build_engine()
    create_schema(direct)
    migrated = upgraded(tmp_path)
    assert set(inspect(direct).get_table_names()) == (
        set(inspect(migrated).get_table_names()) - {"alembic_version"}
    )


def test_the_check_constraints_survive_the_migration(tmp_path):
    engine = upgraded(tmp_path)
    checks = inspect(engine).get_check_constraints("source")
    names = {c["name"] for c in checks}
    assert "ck_source_assay_positive" in names


def test_the_unique_constraints_survive_too(tmp_path):
    engine = upgraded(tmp_path)
    uniques = inspect(engine).get_unique_constraints("weld")
    names = {u["name"] for u in uniques}
    assert "uq_weld_job_id" in names


def test_the_foreign_keys_are_named_by_the_convention(tmp_path):
    engine = upgraded(tmp_path)
    keys = inspect(engine).get_foreign_keys("radiograph")
    assert any(k["name"] == "fk_radiograph_weld_id_weld" for k in keys)


def test_the_config_file_is_where_the_tests_think_it_is():
    assert (ROOT / "alembic.ini").exists()
    assert (ROOT / "migrations" / "env.py").exists()
    with pytest.raises(FileNotFoundError):
        (ROOT / "migrations" / "nothing.py").read_text(encoding="utf-8")


def test_the_hot_lookups_are_indexed(tmp_path):
    engine = upgraded(tmp_path)
    inspector = inspect(engine)
    wanted = {
        "radiograph": {"ix_radiograph_exposed_at", "ix_radiograph_read_at"},
        "survey": {"ix_survey_radiograph_number", "ix_survey_performed_at"},
        "dose_entry": {"ix_dose_entry_badge_year"},
        "weld": {"ix_weld_job_id"},
        "trip": {"ix_trip_source_id_came_back"},
        "indication": {"ix_indication_radiograph_id"},
    }
    for table, expected in wanted.items():
        found = {index["name"] for index in inspector.get_indexes(table)}
        assert expected <= found, table


def test_the_indexes_come_off_again_on_a_downgrade(tmp_path):
    from alembic import command

    url = f"sqlite:///{tmp_path / 'indexes.sqlite3'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    engine = build_engine(url)
    assert set(inspect(engine).get_table_names()) - {"alembic_version"} == set()


def test_there_is_more_than_one_revision_now():
    scripts = ScriptDirectory(str(ROOT / "migrations"))
    assert len(list(scripts.walk_revisions())) >= 2
    assert len(scripts.get_heads()) == 1


def test_every_index_the_models_declare_reaches_the_database(tmp_path):
    engine = upgraded(tmp_path)
    inspector = inspect(engine)
    for table in Base.metadata.tables.values():
        declared = {index.name for index in table.indexes}
        built = {index["name"] for index in inspector.get_indexes(table.name)}
        assert declared <= built, table.name
