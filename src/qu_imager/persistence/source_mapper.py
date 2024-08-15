"""Turning sealed sources into rows and back again.

Both directions are written by hand and neither is generated. That is a
deliberate cost. A generated mapper follows the shape of the class, so renaming
a field silently renames a column, and a column rename in a schema with four
years of data in it is not a refactor, it is a migration with a data step.
Writing it out means the compiler and the tests notice.

The round trip is the thing worth testing rather than either direction on its
own. A pair of mappers that both use the same wrong column produce a perfect
round trip through the objects and lose the data on the way to the database, so
the tests go through a real session rather than calling the two functions back
to back.

State is stored as the enum value rather than its name, because the value is
the phrase a human reads in the utilization log and the name is an
implementation detail that will get renamed one day.
"""

from __future__ import annotations

from qu_imager.errors import ConfigurationError
from qu_imager.persistence.models.sources import LeakTestRow, SourceRow
from qu_imager.sources.isotope import isotope
from qu_imager.sources.sealed import (
    LeakTest,
    SealedSource,
    SourceCertificate,
    SourceState,
)


def source_to_row(source: SealedSource) -> SourceRow:
    """A sealed source, with its leak tests attached to it."""
    certificate = source.certificate
    row = SourceRow(
        serial=certificate.serial,
        isotope_key=certificate.isotope.key,
        assay_activity=certificate.assay_activity,
        assay_at=certificate.assay_at,
        manufacturer=certificate.manufacturer,
        model=certificate.model,
        active_diameter=certificate.active_diameter,
        active_length=certificate.active_length,
        state=source.state.value,
        working_floor=source.working_floor,
    )
    for test in source.leak_tests:
        row.leak_tests.append(leak_test_to_row(test))
    return row


def leak_test_to_row(test: LeakTest) -> LeakTestRow:
    return LeakTestRow(
        performed_at=test.performed_at,
        becquerels_removed=test.becquerels_removed,
        technician=test.technician,
    )


def row_to_source(row: SourceRow) -> SealedSource:
    """A row and its children, back into the object the planner uses."""
    certificate = SourceCertificate(
        serial=row.serial,
        isotope=isotope(row.isotope_key),
        assay_activity=row.assay_activity,
        assay_at=row.assay_at,
        manufacturer=row.manufacturer,
        model=row.model,
        active_diameter=row.active_diameter,
        active_length=row.active_length,
    )
    return SealedSource(
        certificate=certificate,
        state=state_from_value(row.state),
        working_floor=row.working_floor,
        leak_tests=[row_to_leak_test(t) for t in row.leak_tests],
    )


def row_to_leak_test(row: LeakTestRow) -> LeakTest:
    return LeakTest(
        performed_at=row.performed_at,
        becquerels_removed=row.becquerels_removed,
        technician=row.technician,
    )


def state_from_value(value: str) -> SourceState:
    """Read the stored phrase back into a state, refusing anything unknown."""
    for state in SourceState:
        if state.value == value:
            return state
    raise ConfigurationError("no source state with that value", value=value)
