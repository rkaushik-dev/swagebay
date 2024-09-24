"""Turning the domain errors into responses without inventing new ones.

Every error this package raises already carries a stable code and a context
dictionary. The handler puts them straight into the body. It does not rewrite
the message into something friendlier, because the message is what a
coordinator will paste into an email to a client and a friendlier version of it
is a less exact one.

The status codes are the only decision made here and they are a mapping rather
than a judgement. A validation error is the caller's fault, so 422. A conflict
is a state problem rather than a request problem, so 409. A technique error is
a refusal to plan something impossible, which is also the caller's fault but a
different kind, so it gets 422 with its own code so a client can tell them
apart. A configuration error is ours, so 500, and it is the only one that gets
logged as a fault rather than as traffic.
"""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QualificationError,
    QuImagerError,
    SafetyError,
    TechniqueError,
    ValidationError,
)

STATUS_BY_ERROR: tuple[tuple[type[QuImagerError], int], ...] = (
    (NotFoundError, 404),
    (ConflictError, 409),
    (ValidationError, 422),
    (TechniqueError, 422),
    (QualificationError, 422),
    (SafetyError, 409),
    (ConfigurationError, 500),
)


def status_for(error: QuImagerError) -> int:
    """The first mapping that matches, most specific first."""
    for kind, status in STATUS_BY_ERROR:
        if isinstance(error, kind):
            return status
    return 500


def error_body(error: QuImagerError) -> dict[str, object]:
    return error.as_dict()


async def handle(request: Request, error: Exception) -> JSONResponse:
    """The single handler every domain error goes through."""
    if not isinstance(error, QuImagerError):
        raise error
    return JSONResponse(status_code=status_for(error), content=error_body(error))


def install(app: FastAPI) -> None:
    app.add_exception_handler(QuImagerError, handle)
