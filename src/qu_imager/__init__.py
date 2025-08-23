"""Radiographic technique planning and image quality qualification.

What a caller needs first is gathered here so that the common imports are one
line rather than five, and so that moving a module does not break every
consumer. Everything else stays where it is; this is a front door, not a
facade, and reaching past it into a submodule is expected and supported.

The quantities are here because nothing in this package takes a bare number.
A wall is a Length, an activity is an Activity, an exposure is a Duration, and
a dose is a Dose. That is the single decision the rest of the package is built
on, and putting the constructors at the top makes it the first thing anybody
integrating sees.
"""

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
from qu_imager.identity import ClockPosition, JobNumber, WeldId
from qu_imager.quantities.activity import Activity, curie, gbq
from qu_imager.quantities.dose import Dose, DoseRate, usv, usv_per_hour
from qu_imager.quantities.duration import Duration, minutes, seconds
from qu_imager.quantities.length import Length, inch, mm, thou

__version__ = "1.0.0"

__all__ = [
    "Activity",
    "ClockPosition",
    "ConfigurationError",
    "ConflictError",
    "Dose",
    "DoseRate",
    "Duration",
    "JobNumber",
    "Length",
    "NotFoundError",
    "QuImagerError",
    "QualificationError",
    "SafetyError",
    "TechniqueError",
    "ValidationError",
    "WeldId",
    "__version__",
    "curie",
    "gbq",
    "inch",
    "minutes",
    "mm",
    "seconds",
    "thou",
    "usv",
    "usv_per_hour",
]
