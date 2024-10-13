"""Sources over http.

The fitness endpoint takes the moment it is being asked about rather than
assuming now. A coordinator planning tomorrow night's work needs to know
whether the source is fit tomorrow night, and a source whose leak test expires
at midnight is fit when asked at four in the afternoon and not fit for the
shot. Defaulting to now would give the wrong answer confidently.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import (
    ActivityOut,
    BookingIn,
    FitnessOut,
    LengthOut,
    NewSourceIn,
    ReturnIn,
    SourceOut,
)
from qu_imager.persistence.models.sources import SourceRow
from qu_imager.persistence.source_mapper import row_to_source
from qu_imager.quantities.activity import Activity
from qu_imager.services.sources import (
    Booking,
    bring_back,
    fitness,
    location,
    register_source,
    retire,
    send_out,
)
from qu_imager.sources.isotope import isotope
from qu_imager.sources.sealed import SealedSource, SourceCertificate

router = APIRouter(prefix="/sources", tags=["sources"])


@router.post("", status_code=status.HTTP_201_CREATED)
def create_source(body: NewSourceIn, work: Work) -> SourceOut:
    register_source(
        work,
        SealedSource(
            certificate=SourceCertificate(
                serial=body.serial,
                isotope=isotope(body.isotope),
                assay_activity=Activity.from_curie(body.assay_curies),
                assay_at=body.assay_at,
                manufacturer=body.manufacturer,
                model=body.model,
                active_diameter=body.active_diameter.to_length(),
                active_length=body.active_length.to_length(),
            ),
            working_floor=Activity.from_curie(body.working_floor_curies),
        ),
    )
    return _source_out(work, work.sources.get(body.serial))


@router.get("")
def list_sources(work: Work) -> list[SourceOut]:
    return [_source_out(work, row) for row in work.sources.in_service()]


@router.get("/{serial}")
def read_source(serial: str, work: Work) -> SourceOut:
    return _source_out(work, work.sources.get(serial))


@router.get("/{serial}/fitness")
def read_fitness(serial: str, work: Work, at: datetime | None = None) -> FitnessOut:
    report = fitness(work, serial, at or datetime.now())
    return FitnessOut(serial=report.serial, on=report.on, fit=report.fit, reasons=report.reasons)


@router.post("/{serial}/send-out")
def book_out(serial: str, body: BookingIn, work: Work) -> SourceOut:
    send_out(
        work,
        Booking(
            source_serial=serial,
            device_serial=body.device_serial,
            crew_badge=body.crew_badge,
            site=body.site,
            went_out=body.went_out,
        ),
    )
    return _source_out(work, work.sources.get(serial))


@router.post("/{serial}/bring-back")
def book_in(serial: str, body: ReturnIn, work: Work) -> SourceOut:
    bring_back(work, serial, body.came_back, body.exposures)
    return _source_out(work, work.sources.get(serial))


@router.post("/{serial}/retire")
def stand_down(serial: str, work: Work) -> SourceOut:
    retire(work, serial)
    return _source_out(work, work.sources.get(serial))


def _source_out(work: Work, row: SourceRow) -> SourceOut:
    source = row_to_source(row)
    return SourceOut(
        serial=row.serial,
        isotope=row.isotope_key,
        assay_activity=ActivityOut.of(row.assay_activity),
        assay_at=row.assay_at,
        state=row.state,
        location=location(work, row.serial),
        focal_size=LengthOut.of(source.certificate.focal_size),
    )


def today() -> date:
    return date.today()
