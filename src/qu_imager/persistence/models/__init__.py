"""Every table in the schema, gathered so the metadata is complete.

A table is only on the metadata once the module declaring it has been imported.
Anything that calls create_all or lets alembic autogenerate has to import this
package rather than the individual modules, or it will build a schema with
whatever happened to be imported first and no error at all.
"""

from qu_imager.persistence.models.audit import AuditRow
from qu_imager.persistence.models.counters import CounterRow
from qu_imager.persistence.models.maintenance import DefectRow, InspectionRow
from qu_imager.persistence.models.nonconformance import NonconformanceRow
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
from qu_imager.persistence.models.shop import ExchangeRow, UseRow
from qu_imager.persistence.models.sources import (
    DeviceRow,
    LeakTestRow,
    SightingRow,
    SourceRow,
    TripRow,
)
from qu_imager.persistence.models.store import StoredImageRow
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
    "DefectRow",
    "FiledRow",
    "DeviceRow",
    "DoseEntryRow",
    "ExchangeRow",
    "DosimeterRow",
    "FilmRow",
    "IncidentRow",
    "IndicationRow",
    "InspectionRow",
    "IsotopeRow",
    "JobRow",
    "LeakTestRow",
    "LotRow",
    "MaterialRow",
    "NonconformanceRow",
    "QualificationRow",
    "RadiographRow",
    "SightingRow",
    "SourceRow",
    "StoredImageRow",
    "SurveyPointRow",
    "SurveyRow",
    "TripRow",
    "UseRow",
    "WeldRow",
]
