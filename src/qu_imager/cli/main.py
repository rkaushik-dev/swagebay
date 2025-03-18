"""The command line entry point.

Two things this does that are worth saying out loud.

Every command opens its own unit of work and closes it. There is no persistent
session and no connection held between commands, because the tool is run from a
site laptop over a link that drops, and a held connection is a hung command
that somebody kills with the transaction half done.

Domain errors are caught here and printed as one line with the code on it,
rather than traced. A radiographer at three in the morning does not want a
stack trace, and the code on the line is enough for the office to find the
problem the next day. The exit status carries the class of failure so a script
can branch on it without parsing the message.
"""

from __future__ import annotations

import sys
from collections.abc import Iterator
from contextlib import contextmanager

import click

from qu_imager import __version__
from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QuImagerError,
    TechniqueError,
    ValidationError,
)
from qu_imager.persistence.unit_of_work import (
    UnitOfWork,
    build_engine,
    create_schema,
    unit_of_work_factory,
)

EXIT_OK = 0
EXIT_VALIDATION = 2
EXIT_NOT_FOUND = 3
EXIT_CONFLICT = 4
EXIT_TECHNIQUE = 5
EXIT_CONFIGURATION = 6
EXIT_OTHER = 1

_STATUS: tuple[tuple[type[QuImagerError], int], ...] = (
    (NotFoundError, EXIT_NOT_FOUND),
    (ConflictError, EXIT_CONFLICT),
    (ValidationError, EXIT_VALIDATION),
    (TechniqueError, EXIT_TECHNIQUE),
    (ConfigurationError, EXIT_CONFIGURATION),
)


def exit_status(error: QuImagerError) -> int:
    for kind, status in _STATUS:
        if isinstance(error, kind):
            return status
    return EXIT_OTHER


@click.group()
@click.option(
    "--database",
    envvar="QU_IMAGER_DATABASE",
    default="sqlite:///qu_imager.sqlite3",
    show_default=True,
    help="sqlalchemy url of the database to work against",
)
@click.version_option(__version__, prog_name="qu-imager")
@click.pass_context
def cli(ctx: click.Context, database: str) -> None:
    """Radiographic technique planning and image quality qualification."""
    ctx.ensure_object(dict)
    ctx.obj["database"] = database


@cli.command("init")
@click.pass_context
def init(ctx: click.Context) -> None:
    """Build the schema in an empty database. Real ones use the migrations."""
    engine = build_engine(ctx.obj["database"])
    create_schema(engine)
    click.echo(f"schema built in {ctx.obj['database']}")


@contextmanager
def work_for(ctx: click.Context) -> Iterator[UnitOfWork]:
    """One unit of work for one command, committed if the command returns."""
    factory = unit_of_work_factory(build_engine(ctx.obj["database"]))
    unit = UnitOfWork(factory)
    with unit:
        yield unit
        unit.commit()


def fail(error: QuImagerError) -> None:
    """Print one line and leave with the status that names the class of failure."""
    click.echo(f"{error.code}: {error}", err=True)
    raise SystemExit(exit_status(error))


def load_commands() -> None:
    """Import the command modules so their decorators register the commands."""
    from qu_imager.cli import (  # noqa: F401
        catalogue,
        jobs,
        reports,
        safety,
        sources,
        technique,
        welds,
    )


def run() -> None:
    """The console script entry point, with the error translation round it."""
    load_commands()
    try:
        cli.main(standalone_mode=False, obj={})
    except QuImagerError as error:
        fail(error)
    except click.ClickException as usage:
        usage.show()
        raise SystemExit(EXIT_VALIDATION) from usage
    except click.exceptions.Abort:
        click.echo("aborted", err=True)
        raise SystemExit(EXIT_OTHER) from None
    sys.exit(EXIT_OK)
