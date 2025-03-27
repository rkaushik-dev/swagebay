"""Queries for the five small registers.

Every one of these answers the same question in its own words: what is
outstanding today. The incident register asks what is unreported, the
calibration register what has expired, the stock what has gone out of date, the
archive what is due for destruction, and the qualification register who has
gone quiet.

They are written out separately rather than through one generic overdue query,
because the date each one compares against is a different column with a
different meaning and a generic query would need the column name as an
argument, which is a query with a hole in it.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import NotFoundError, ValidationError
from qu_imager.persistence.models.registers import (
    CalibrationRow,
    FiledRow,
    IncidentRow,
    LotRow,
    QualificationRow,
)


class IncidentRepository:
    """Abnormal occurrences and what is owed."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def find(self, reference: str) -> IncidentRow | None:
        return self.session.scalars(
            select(IncidentRow).where(IncidentRow.reference == reference)
        ).one_or_none()

    def get(self, reference: str) -> IncidentRow:
        found = self.find(reference)
        if found is None:
            raise NotFoundError("incident", reference)
        return found

    def unreported(self) -> tuple[IncidentRow, ...]:
        return tuple(
            self.session.scalars(
                select(IncidentRow)
                .where(IncidentRow.notified_at.is_(None))
                .order_by(IncidentRow.discovered_at, IncidentRow.reference)
            ).all()
        )

    def since(self, moment: datetime) -> tuple[IncidentRow, ...]:
        return tuple(
            self.session.scalars(
                select(IncidentRow)
                .where(IncidentRow.discovered_at >= moment)
                .order_by(IncidentRow.discovered_at)
            ).all()
        )


class CalibrationRepository:
    """Instruments and their certificates."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def latest_for(self, serial: str) -> CalibrationRow | None:
        return self.session.scalars(
            select(CalibrationRow)
            .where(CalibrationRow.serial == serial)
            .order_by(CalibrationRow.calibrated_on.desc())
        ).first()

    def expired_on(self, day: date) -> tuple[str, ...]:
        serials = self.session.scalars(select(CalibrationRow.serial).distinct()).all()
        out: list[str] = []
        for serial in sorted(serials):
            latest = self.latest_for(serial)
            if latest is None or latest.due_on <= day:
                out.append(serial)
        return tuple(out)

    def due_within(self, day: date, days: int) -> tuple[str, ...]:
        if days < 0:
            raise ValidationError("a look ahead is not negative", days=days)
        horizon = day + timedelta(days=days)
        serials = self.session.scalars(select(CalibrationRow.serial).distinct()).all()
        out: list[str] = []
        for serial in sorted(serials):
            latest = self.latest_for(serial)
            if latest is not None and day < latest.due_on <= horizon:
                out.append(serial)
        return tuple(out)


class StockRepository:
    """Film on the shelf."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def usable_on(self, day: date, film_key: str, size: str) -> tuple[LotRow, ...]:
        return tuple(
            self.session.scalars(
                select(LotRow)
                .where(
                    LotRow.film_key == film_key,
                    LotRow.size == size,
                    LotRow.sheets > 0,
                    LotRow.expires_on > day,
                )
                .order_by(LotRow.expires_on, LotRow.lot_number)
            ).all()
        )

    def on_hand(self, day: date, film_key: str, size: str) -> int:
        return sum(lot.sheets for lot in self.usable_on(day, film_key, size))

    def expired_on(self, day: date) -> tuple[str, ...]:
        rows = self.session.scalars(
            select(LotRow)
            .where(LotRow.expires_on <= day, LotRow.sheets > 0)
            .order_by(LotRow.lot_number)
        ).all()
        return tuple(row.lot_number for row in rows)


class ArchiveRepository:
    """The filing cabinet."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def find(self, radiograph: str) -> FiledRow | None:
        return self.session.scalars(
            select(FiledRow).where(FiledRow.radiograph == radiograph)
        ).one_or_none()

    def for_job(self, job: str) -> tuple[FiledRow, ...]:
        return tuple(
            self.session.scalars(
                select(FiledRow).where(FiledRow.job == job).order_by(FiledRow.radiograph)
            ).all()
        )

    def without_a_location(self) -> tuple[str, ...]:
        rows = self.session.scalars(
            select(FiledRow).where(FiledRow.box.is_(None)).order_by(FiledRow.radiograph)
        ).all()
        return tuple(row.radiograph for row in rows)


class QualificationRepository:
    """Welder qualifications and their continuity."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def for_welder(self, welder: str) -> tuple[QualificationRow, ...]:
        return tuple(
            self.session.scalars(
                select(QualificationRow)
                .where(QualificationRow.welder == welder)
                .order_by(QualificationRow.qualified_on)
            ).all()
        )

    def lapsed_on(self, day: date, continuity_days: int = 180) -> tuple[tuple[str, str], ...]:
        if continuity_days < 1:
            raise ValidationError("continuity is a positive number of days")
        cutoff = day - timedelta(days=continuity_days)
        rows = self.session.scalars(
            select(QualificationRow)
            .where(QualificationRow.last_used_on <= cutoff)
            .order_by(QualificationRow.welder, QualificationRow.process)
        ).all()
        seen: list[tuple[str, str]] = []
        for row in rows:
            key = (row.welder, row.process)
            if key not in seen:
                seen.append(key)
        return tuple(seen)
