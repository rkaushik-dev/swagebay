"""The error hierarchy every other module raises through.

Two things are going on here and they are worth keeping apart. A
``TechniqueError`` means the shot as described cannot be made: the geometry is
impossible, the source is spent, the chart does not reach that thickness. A
``QualificationError`` means the shot was made and the image it produced is not
admissible. The first stops work before the source comes out of the pig. The
second is a finding against an image that already exists, and somebody has to
be told which one and why.

Every error carries a stable ``code``. The codes end up in report packages that
are read years later by people who no longer have the software, so they do not
get renamed once they ship.
"""

from __future__ import annotations

from typing import Any


class QuImagerError(Exception):
    """Base for everything this package raises deliberately."""

    code = "qi.error"

    def __init__(self, message: str, **context: Any) -> None:
        super().__init__(message)
        self.message = message
        self.context = context

    def __str__(self) -> str:
        if not self.context:
            return self.message
        rendered = ", ".join(f"{k}={v!r}" for k, v in sorted(self.context.items()))
        return f"{self.message} ({rendered})"

    def as_dict(self) -> dict[str, Any]:
        """The shape that goes into a report or an api response body."""
        return {"code": self.code, "message": self.message, "context": dict(self.context)}


class ConfigurationError(QuImagerError):
    """A rule table, a chart or a procedure is missing or self contradictory."""

    code = "qi.configuration"


class ValidationError(QuImagerError):
    """A value handed in by a caller is outside what the domain allows."""

    code = "qi.validation"


class UnitError(ValidationError):
    """Two quantities were combined that do not share a dimension."""

    code = "qi.unit"


class TechniqueError(QuImagerError):
    """The shot as described cannot be made."""

    code = "qi.technique"


class QualificationError(QuImagerError):
    """An image that exists is not admissible."""

    code = "qi.qualification"


class SafetyError(QuImagerError):
    """A radiation safety limit would be exceeded, or a control is missing."""

    code = "qi.safety"


class NotFoundError(QuImagerError):
    """A record that was asked for by identity does not exist."""

    code = "qi.not_found"

    def __init__(self, kind: str, identity: object) -> None:
        super().__init__(f"no {kind} with identity {identity!r}", kind=kind, identity=identity)
        self.kind = kind
        self.identity = identity


class ConflictError(QuImagerError):
    """A write would break an invariant that spans more than one record."""

    code = "qi.conflict"
