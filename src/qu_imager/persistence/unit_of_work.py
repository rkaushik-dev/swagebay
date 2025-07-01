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

from collections.abc import Callable, Iterator
from contextlib import contextmanager
from types import TracebackType
from typing import Any, Generic, TypeVar, cast, overload

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from qu_imager.errors import ConflictError
from qu_imager.persistence.audit_repository import AuditRepository
from qu_imager.persistence.base import Base
from qu_imager.persistence.maintenance_repository import MaintenanceRepository
from qu_imager.persistence.nonconformance_repository import NonconformanceRepository
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
from qu_imager.persistence.store_repository import StoreRepository


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


T = TypeVar("T")


class Opened(Generic[T]):
    """A repository that exists while the unit of work is open and not before.

    Ten of these were written out by hand: an optional attribute, a line in the
    constructor, a line in the enter, a four line property that raised when it
    was None, and a line in the exit that put it back. Fifty lines of the same
    thing, and the eleventh was added by copying the tenth and changing two
    words, which is how the tenth came to be there as well.

    The descriptor keeps the refusal in one place and puts the repository's
    real type on the attribute, so callers do not see an optional and the enter
    and exit no longer have a list to keep in step with the constructor. The
    value lives in the instance dictionary rather than in a private attribute,
    which is what makes the closed state absent rather than None.
    """

    def __init__(self, build: Callable[[Session], T]) -> None:
        self.build = build
        self.name = ""

    def __set_name__(self, owner: type, name: str) -> None:
        self.name = name

    @overload
    def __get__(self, instance: None, owner: type | None = None) -> Opened[T]: ...

    @overload
    def __get__(self, instance: object, owner: type | None = None) -> T: ...

    def __get__(self, instance: object | None, owner: type | None = None) -> Opened[T] | T:
        if instance is None:
            return self
        try:
            return cast(T, instance.__dict__[self.name])
        except KeyError:
            raise ConflictError("this unit of work is not open") from None

    def open(self, instance: object, session: Session) -> None:
        instance.__dict__[self.name] = self.build(session)

    def close(self, instance: object) -> None:
        instance.__dict__.pop(self.name, None)


class UnitOfWork:
    """A transaction, with the repositories that share it."""

    sources = Opened(SourceRepository)
    jobs = Opened(JobRepository)
    welds = Opened(WeldRepository)
    numbering = Opened(Numbering)
    incidents = Opened(IncidentRepository)
    calibrations = Opened(CalibrationRepository)
    stock = Opened(StockRepository)
    archive = Opened(ArchiveRepository)
    qualifications = Opened(QualificationRepository)
    audit = Opened(AuditRepository)
    store = Opened(StoreRepository)
    maintenance = Opened(MaintenanceRepository)
    nonconformances = Opened(NonconformanceRepository)

    def __init__(self, factory: sessionmaker[Session]) -> None:
        self._factory = factory
        self._session: Session | None = None

    @classmethod
    def _repositories(cls) -> tuple[Opened[Any], ...]:
        return tuple(value for value in vars(cls).values() if isinstance(value, Opened))

    def __enter__(self) -> UnitOfWork:
        self._session = self._factory()
        for repository in self._repositories():
            repository.open(self, self._session)
        return self

    @property
    def is_open(self) -> bool:
        return self._session is not None

    @property
    def session(self) -> Session:
        """The session, or a refusal. Never None, which is the whole point."""
        if self._session is None:
            raise ConflictError("this unit of work is not open")
        return self._session

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
            for repository in self._repositories():
                repository.close(self)

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
