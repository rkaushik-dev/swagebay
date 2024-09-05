"""Alembic environment.

The star import is the point of this file. Alembic autogenerates by comparing
the database against Base.metadata, and a table whose module has not been
imported is simply not on the metadata, so autogenerate would cheerfully write
a migration that drops it. Importing the models package pulls all of them in.
"""

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from qu_imager.persistence.base import Base
from qu_imager.persistence.models import *  # noqa: F401, F403

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def render_item(type_, obj, autogen_context):
    """Render the value object column types as qi.Something rather than a path.

    Without this alembic writes the fully qualified class name into the
    migration and then does not import it, so every generated migration is
    dead on arrival with a NameError. Found the first time the baseline was
    run rather than generated.
    """
    if type_ == "type" and type(obj).__module__ == "qu_imager.persistence.types":
        autogen_context.imports.add("import qu_imager.persistence.types as qi")
        return f"qi.{type(obj).__name__}()"
    return False


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        render_as_batch=True,
        render_item=render_item,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            render_as_batch=True,
            render_item=render_item,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
