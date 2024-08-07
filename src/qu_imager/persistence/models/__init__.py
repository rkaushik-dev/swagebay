"""Every table in the schema, gathered so the metadata is complete.

A table is only on the metadata once the module declaring it has been imported.
Anything that calls create_all or lets alembic autogenerate has to import this
package rather than the individual modules, or it will build a schema with
whatever happened to be imported first and no error at all.
"""

from qu_imager.persistence.models.reference import FilmRow, IsotopeRow, MaterialRow
from qu_imager.persistence.models.sources import (
    DeviceRow,
    LeakTestRow,
    SightingRow,
    SourceRow,
    TripRow,
)

__all__ = [
    "DeviceRow",
    "FilmRow",
    "IsotopeRow",
    "LeakTestRow",
    "MaterialRow",
    "SightingRow",
    "SourceRow",
    "TripRow",
]
