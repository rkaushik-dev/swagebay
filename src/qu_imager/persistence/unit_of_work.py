"""One transaction, one session, and every repository built on it.

The rule this enforces is that a piece of work either happens completely or not
at all, and the piece of work is decided by the caller rather than by whichever
repository was called last. Reading a weld, taking a radiograph number, writing
the radiograph and appending the indications are one thing. Committing after
the number is taken and before the radiograph is written leaves a number issued
against nothing, which is exactly the hole in the sequence the counter design
exists to prevent.

Leaving the block by any route other than the end rolls back. That includes an
exception the caller catches outside, a return from inside the block, and an
explicit rollback, and it is why the commit is at the end of the body rather
than in a finally.

The session is not hidden. The migrations and the tests need it, so it is named
plainly rather than with an underscore, and production callers go through the
repositories so that queries stay in one place.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from types import TracebackType

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker

from qu_imager.errors import ConflictError
from qu_imager.persistence.base import Base
from qu_imager.persistence.numbering import Numbering
from qu_imager.persistence.repositories import (
    JobRepository,
    SourceRepository,
    WeldRepository,
)


def build_engine(url: str = "sqlite://", echo: bool = False) -> Engine:
    return create_engine(url, echo=echo, future=True)


def create_schema(engine: Engine) -> None:
    """Only for tests and for a throwaway database. Real ones use alembic."""
    import qu_imager.persistence.models  # noqa: F401

    Base.metadata.create_all(engine)


class UnitOfWork:
    """A transaction, with the repositories that share it."""

    def __init__(self, factory: sessionmaker[Session]) -> None:
        self._factory = factory
        self.session: Session | None = None
        self.sources: SourceRepository | None = None
        self.jobs: JobRepository | None = None
        self.welds: WeldRepository | None = None
        self.numbering: Numbering | None = None

    def __enter__(self) -> UnitOfWork:
        self.session = self._factory()
        self.sources = SourceRepository(self.session)
        self.jobs = JobRepository(self.session)
        self.welds = WeldRepository(self.session)
        self.numbering = Numbering(self.session)
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        if self.session is None:
            return
        try:
            self.session.rollback()
        finally:
            self.session.close()
            self.session = None
            self.sources = None
            self.jobs = None
            self.welds = None
            self.numbering = None

    def commit(self) -> None:
        self._open().commit()

    def rollback(self) -> None:
        self._open().rollback()

    def flush(self) -> None:
        self._open().flush()

    def _open(self) -> Session:
        if self.session is None:
            raise ConflictError("this unit of work is not open")
        return self.session


def unit_of_work_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False)


@contextmanager
def transaction(factory: sessionmaker[Session]) -> Iterator[UnitOfWork]:
    """Open a unit of work, commit at the end of the block, roll back otherwise."""
    work = UnitOfWork(factory)
    with work:
        yield work
        work.commit()
