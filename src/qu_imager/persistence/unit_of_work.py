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
from sqlalchemy.pool import StaticPool

from qu_imager.errors import ConflictError
from qu_imager.persistence.base import Base
from qu_imager.persistence.numbering import Numbering
from qu_imager.persistence.register_repositories import (
    ArchiveRepository,
    CalibrationRepository,
    IncidentRepository,
    QualificationRepository,
    StockRepository,
)
from qu_imager.persistence.repositories import (
    JobRepository,
    SourceRepository,
    WeldRepository,
)


def build_engine(url: str = "sqlite://", echo: bool = False) -> Engine:
    """An engine, with the one sqlite quirk that has to be handled here.

    An in memory sqlite database belongs to the connection that made it, and
    the default pool hands a different connection to every thread. A web
    request served on a worker thread therefore gets an empty database and the
    failure reads as a missing table, which sends everybody looking at the
    migrations. A static pool shares the one connection and the memory database
    behaves the way anybody would expect.

    Nothing changes for a real file backed database, which is the only kind
    that runs anywhere other than a test.
    """
    if url in {"sqlite://", "sqlite:///:memory:"}:
        return create_engine(
            url,
            echo=echo,
            future=True,
            poolclass=StaticPool,
            connect_args={"check_same_thread": False},
        )
    return create_engine(url, echo=echo, future=True)


def create_schema(engine: Engine) -> None:
    """Only for tests and for a throwaway database. Real ones use alembic."""
    import qu_imager.persistence.models  # noqa: F401

    Base.metadata.create_all(engine)


class UnitOfWork:
    """A transaction, with the repositories that share it."""

    def __init__(self, factory: sessionmaker[Session]) -> None:
        self._factory = factory
        self._session: Session | None = None
        self._sources: SourceRepository | None = None
        self._jobs: JobRepository | None = None
        self._welds: WeldRepository | None = None
        self._numbering: Numbering | None = None
        self._incidents: IncidentRepository | None = None
        self._calibrations: CalibrationRepository | None = None
        self._stock: StockRepository | None = None
        self._archive: ArchiveRepository | None = None
        self._qualifications: QualificationRepository | None = None

    def __enter__(self) -> UnitOfWork:
        self._session = self._factory()
        self._sources = SourceRepository(self._session)
        self._jobs = JobRepository(self._session)
        self._welds = WeldRepository(self._session)
        self._numbering = Numbering(self._session)
        self._incidents = IncidentRepository(self._session)
        self._calibrations = CalibrationRepository(self._session)
        self._stock = StockRepository(self._session)
        self._archive = ArchiveRepository(self._session)
        self._qualifications = QualificationRepository(self._session)
        return self

    @property
    def is_open(self) -> bool:
        return self._session is not None

    @property
    def session(self) -> Session:
        """The session, or a refusal. Never None, which is the whole point.

        The repositories used to be plain attributes typed as optional, so
        every caller had to prove they were not None before using them and the
        proof was three lines of noise at the top of every handler. Making them
        properties that refuse moves the check to one place and takes the
        optional out of the type.
        """
        if self._session is None:
            raise ConflictError("this unit of work is not open")
        return self._session

    @property
    def sources(self) -> SourceRepository:
        if self._sources is None:
            raise ConflictError("this unit of work is not open")
        return self._sources

    @property
    def jobs(self) -> JobRepository:
        if self._jobs is None:
            raise ConflictError("this unit of work is not open")
        return self._jobs

    @property
    def welds(self) -> WeldRepository:
        if self._welds is None:
            raise ConflictError("this unit of work is not open")
        return self._welds

    @property
    def numbering(self) -> Numbering:
        if self._numbering is None:
            raise ConflictError("this unit of work is not open")
        return self._numbering

    @property
    def incidents(self) -> IncidentRepository:
        if self._incidents is None:
            raise ConflictError("this unit of work is not open")
        return self._incidents

    @property
    def calibrations(self) -> CalibrationRepository:
        if self._calibrations is None:
            raise ConflictError("this unit of work is not open")
        return self._calibrations

    @property
    def stock(self) -> StockRepository:
        if self._stock is None:
            raise ConflictError("this unit of work is not open")
        return self._stock

    @property
    def archive(self) -> ArchiveRepository:
        if self._archive is None:
            raise ConflictError("this unit of work is not open")
        return self._archive

    @property
    def qualifications(self) -> QualificationRepository:
        if self._qualifications is None:
            raise ConflictError("this unit of work is not open")
        return self._qualifications

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        if self._session is None:
            return
        try:
            self._session.rollback()
        finally:
            self._session.close()
            self._session = None
            self._sources = None
            self._jobs = None
            self._welds = None
            self._numbering = None
            self._incidents = None
            self._calibrations = None
            self._stock = None
            self._archive = None
            self._qualifications = None

    def commit(self) -> None:
        self.session.commit()

    def rollback(self) -> None:
        self.session.rollback()

    def flush(self) -> None:
        self.session.flush()


def unit_of_work_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False)


@contextmanager
def transaction(factory: sessionmaker[Session]) -> Iterator[UnitOfWork]:
    """Open a unit of work, commit at the end of the block, roll back otherwise."""
    work = UnitOfWork(factory)
    with work:
        yield work
        work.commit()
