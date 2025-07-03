"""The application, and the one dependency everything else hangs off.

The session factory is stored on the application state rather than being a
module level global. That is the difference between a test that can stand up
two applications against two databases and a test suite that has to be run in
one process against one, and the second kind stops being runnable at about
forty tests.

There is no middleware here beyond the error handler. Authentication is done by
the reverse proxy in front of this, which is where the client certificates
live, and putting a second half implemented copy of it in here would be worse
than having none.
"""

from __future__ import annotations

from collections.abc import Iterator
from typing import Annotated

from fastapi import Depends, FastAPI, Request
from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from qu_imager import __version__
from qu_imager.api import errors
from qu_imager.persistence.unit_of_work import (
    UnitOfWork,
    build_engine,
    create_schema,
    unit_of_work_factory,
)


def build_app(factory: sessionmaker[Session]) -> FastAPI:
    """One application, bound to one session factory."""
    app = FastAPI(
        title="qu_imager",
        version=__version__,
        summary="radiographic technique planning and image quality qualification",
    )
    app.state.session_factory = factory
    errors.install(app)
    _install_routers(app)

    @app.get("/health", tags=["service"])
    def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


def build_app_for(engine: Engine, create: bool = False) -> FastAPI:
    if create:
        create_schema(engine)
    return build_app(unit_of_work_factory(engine))


def build_default_app(url: str = "sqlite:///qu_imager.sqlite3") -> FastAPI:
    return build_app_for(build_engine(url))


def get_work(request: Request) -> Iterator[UnitOfWork]:
    """One unit of work per request, committed only if the handler returns.

    A handler that raises leaves the block by the exception route, so the
    context manager rolls back and the commit line is never reached. That is
    the whole transactional contract of the api and it is three lines long
    because the unit of work already carries it.
    """
    factory: sessionmaker[Session] = request.app.state.session_factory
    work = UnitOfWork(factory)
    with work:
        yield work
        work.commit()


Work = Annotated[UnitOfWork, Depends(get_work)]


def _install_routers(app: FastAPI) -> None:
    """Imported inside the function because the routers import this module."""
    from qu_imager.api.routes import (
        devices,
        jobs,
        nonconformance,
        radiographs,
        registers,
        reports,
        safety,
        sources,
        technique,
    )

    app.include_router(jobs.router)
    app.include_router(devices.router)
    app.include_router(nonconformance.router)
    app.include_router(sources.router)
    app.include_router(technique.router)
    app.include_router(radiographs.router)
    app.include_router(safety.router)
    app.include_router(reports.router)
    app.include_router(registers.router)
