"""Repositories, which are the only place a query is written.

A repository here is not an abstraction over the database. It is a place to put
the queries so that they are all in one file and can be read together, and so
that a query written for one caller gets reused by the next one instead of
being written slightly differently.

Nothing here opens or closes a transaction. A repository is handed a session
and uses it, and whoever owns the session decides when to commit. A repository
that commits is a repository that cannot be used twice in one unit of work,
which is discovered late and hurts.

Every finder that can miss returns None and every finder that must not miss
raises NotFoundError with the identity in it. Mixing the two, so that one
method returns None and another raises, is the thing that produces a caller
full of defensive checks in the places that do not need them and none in the
places that do.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, selectinload

from qu_imager.errors import NotFoundError, ValidationError
from qu_imager.identity import JobNumber, WeldId
from qu_imager.persistence.models.sources import DeviceRow, SourceRow, TripRow
from qu_imager.persistence.models.work import JobRow, RadiographRow, WeldRow
from qu_imager.safety.inventory import Trip


class SourceRepository:
    """Sealed sources and the devices they ride in."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def find(self, serial: str) -> SourceRow | None:
        return self.session.scalars(
            select(SourceRow).where(SourceRow.serial == serial)
        ).one_or_none()

    def get(self, serial: str) -> SourceRow:
        found = self.find(serial)
        if found is None:
            raise NotFoundError("source", serial)
        return found

    def in_service(self) -> tuple[SourceRow, ...]:
        """Every source that can go out, with its wipe tests already loaded.

        Whether a source is fit is a question about its last wipe test, so
        every caller of this walks the relationship. Leaving it lazy made the
        morning sheet read once per source and the count grew with the shop.
        """
        return tuple(
            self.session.scalars(
                select(SourceRow)
                .options(selectinload(SourceRow.leak_tests))
                .where(SourceRow.state == "in service")
                .order_by(SourceRow.serial)
            ).all()
        )

    def open_trip(self, serial: str) -> TripRow | None:
        source = self.get(serial)
        return self.session.scalars(
            select(TripRow).where(TripRow.source_id == source.id, TripRow.came_back.is_(None))
        ).one_or_none()

    def trips_between(self, since: date, until: date) -> tuple[Trip, ...]:
        """Every trip that overlapped a window, as the domain object.

        The overlap test is deliberately not an equality on the departure
        date. A trip that went out in June and came back in July is a July
        trip as far as the utilisation log is concerned, and a query keyed on
        went_out alone silently drops it.
        """
        rows = self.session.execute(
            select(
                SourceRow.serial,
                DeviceRow.serial,
                TripRow.crew_badge,
                TripRow.site,
                TripRow.went_out,
                TripRow.came_back,
                TripRow.exposures,
            )
            .join(TripRow, TripRow.source_id == SourceRow.id)
            .join(DeviceRow, DeviceRow.id == TripRow.device_id)
            .where(
                TripRow.went_out <= until,
                or_(TripRow.came_back.is_(None), TripRow.came_back >= since),
            )
            .order_by(TripRow.went_out, TripRow.id)
        ).all()
        return tuple(
            Trip(
                source_serial=source,
                device_serial=device,
                crew_badge=badge,
                site=site,
                went_out=went,
                came_back=back,
                exposures=exposures,
            )
            for source, device, badge, site, went, back, exposures in rows
        )

    def open_trip_serials(self) -> frozenset[str]:
        """Every source that is out, in one query rather than one each."""
        rows = self.session.execute(
            select(SourceRow.serial)
            .join(TripRow, TripRow.source_id == SourceRow.id)
            .where(TripRow.came_back.is_(None))
        ).all()
        return frozenset(serial for (serial,) in rows)

    def find_device(self, serial: str) -> DeviceRow | None:
        return self.session.scalars(
            select(DeviceRow).where(DeviceRow.serial == serial)
        ).one_or_none()

    def device(self, serial: str) -> DeviceRow:
        found = self.find_device(serial)
        if found is None:
            raise NotFoundError("device", serial)
        return found

    def devices(self) -> tuple[DeviceRow, ...]:
        return tuple(self.session.scalars(select(DeviceRow).order_by(DeviceRow.serial)).all())

    def add(self, source: SourceRow) -> None:
        self.session.add(source)


class JobRepository:
    """Jobs, and the welds under them."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def find(self, number: JobNumber) -> JobRow | None:
        return self.session.scalars(
            select(JobRow).where(JobRow.year == number.year, JobRow.serial == number.serial)
        ).one_or_none()

    def get(self, number: JobNumber) -> JobRow:
        found = self.find(number)
        if found is None:
            raise NotFoundError("job", str(number))
        return found

    def open_on(self, day: date) -> tuple[JobRow, ...]:
        return tuple(
            self.session.scalars(
                select(JobRow)
                .where(JobRow.opened_on <= day)
                .where((JobRow.closed_on.is_(None)) | (JobRow.closed_on >= day))
                .order_by(JobRow.year, JobRow.serial)
            ).all()
        )

    def next_serial(self, year: int) -> int:
        used = self.session.scalars(
            select(JobRow.serial).where(JobRow.year == year).order_by(JobRow.serial.desc())
        ).first()
        return 1 if used is None else used + 1

    def add(self, job: JobRow) -> None:
        self.session.add(job)


class WeldRepository:
    """Welds and their radiographs."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def find(self, job: JobRow, weld: WeldId) -> WeldRow | None:
        return self.session.scalars(
            select(WeldRow).where(
                WeldRow.job_id == job.id,
                WeldRow.prefix == weld.prefix,
                WeldRow.number == weld.number,
                WeldRow.repair_round == weld.repair,
            )
        ).one_or_none()

    def get(self, job: JobRow, weld: WeldId) -> WeldRow:
        found = self.find(job, weld)
        if found is None:
            raise NotFoundError("weld", str(weld))
        return found

    def rounds(self, job: JobRow, weld: WeldId) -> tuple[WeldRow, ...]:
        """Every round of one weld, original first."""
        return tuple(
            self.session.scalars(
                select(WeldRow)
                .where(
                    WeldRow.job_id == job.id,
                    WeldRow.prefix == weld.prefix,
                    WeldRow.number == weld.number,
                )
                .order_by(WeldRow.repair_round)
            ).all()
        )

    def radiograph(self, number: str) -> RadiographRow:
        found = self.session.scalars(
            select(RadiographRow).where(RadiographRow.number == number)
        ).one_or_none()
        if found is None:
            raise NotFoundError("radiograph", number)
        return found

    def unread(self, job: JobRow) -> tuple[RadiographRow, ...]:
        return tuple(
            self.session.scalars(
                select(RadiographRow)
                .join(WeldRow)
                .where(WeldRow.job_id == job.id, RadiographRow.read_at.is_(None))
                .order_by(RadiographRow.number)
            ).all()
        )

    def radiographs_after(
        self, job: JobRow, cursor: str | None, limit: int
    ) -> tuple[RadiographRow, ...]:
        """One page of a job's radiographs, anchored on the number the client saw.

        The number is unique across the shop, so it is a sound cursor on its
        own and no tie break column is needed. That is the only reason the
        cursor can be the number rather than an opaque token.
        """
        if limit < 1:
            raise ValidationError("a page holds at least one row", limit=limit)
        statement = (
            select(RadiographRow)
            .join(WeldRow)
            .where(WeldRow.job_id == job.id)
            .order_by(RadiographRow.number)
            .limit(limit)
        )
        if cursor is not None:
            statement = statement.where(RadiographRow.number > cursor)
        return tuple(self.session.scalars(statement).all())

    def inadmissible(self, job: JobRow) -> tuple[RadiographRow, ...]:
        return tuple(
            self.session.scalars(
                select(RadiographRow)
                .join(WeldRow)
                .where(WeldRow.job_id == job.id, RadiographRow.admissible.is_(False))
                .order_by(RadiographRow.number)
            ).all()
        )
