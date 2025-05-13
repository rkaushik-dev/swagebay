"""Every table in the schema, gathered so the metadata is complete.

A table is only on the metadata once the module declaring it has been imported.
Anything that calls create_all or lets alembic autogenerate has to import this
package rather than the individual modules, or it will build a schema with
whatever happened to be imported first and no error at all.
"""

from qu_imager.persistence.models.audit import AuditRow
from qu_imager.persistence.models.counters import CounterRow
from qu_imager.persistence.models.reference import FilmRow, IsotopeRow, MaterialRow
from qu_imager.persistence.models.registers import (
    CalibrationRow,
    ControlStripRow,
    FiledRow,
    IncidentRow,
    LotRow,
    QualificationRow,
)
from qu_imager.persistence.models.safety import (
    DoseEntryRow,
    DosimeterRow,
    SurveyPointRow,
    SurveyRow,
)
from qu_imager.persistence.models.sources import (
    DeviceRow,
    LeakTestRow,
    SightingRow,
    SourceRow,
    TripRow,
)
from qu_imager.persistence.models.work import (
    IndicationRow,
    JobRow,
    RadiographRow,
    WeldRow,
)

__all__ = [
    "AuditRow",
    "CalibrationRow",
    "ControlStripRow",
    "CounterRow",
    "FiledRow",
    "DeviceRow",
    "DoseEntryRow",
    "DosimeterRow",
    "FilmRow",
    "IncidentRow",
    "IndicationRow",
    "IsotopeRow",
    "JobRow",
    "LeakTestRow",
    "LotRow",
    "MaterialRow",
    "QualificationRow",
    "RadiographRow",
    "SightingRow",
    "SourceRow",
    "SurveyPointRow",
    "SurveyRow",
    "TripRow",
    "WeldRow",
]
