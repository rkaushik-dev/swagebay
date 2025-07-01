"""Reading and writing non conformance reports.

Deciding and closing go through the domain object rather than being written as
column updates, which is why both methods load, call, and let the mapper write
back. Setting a disposition with an update statement would skip the rule that
use as is needs a concession, and that rule is the only reason the register
exists.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.nonconformance import Disposition, Register, Report
from qu_imager.persistence.models.nonconformance import NonconformanceRow


def row_to_report(row: NonconformanceRow) -> Report:
    return Report(
        reference=row.reference,
        job=row.job,
        item=row.item,
        requirement=row.requirement,
        account=row.account,
        raised_on=row.raised_on,
        raised_by=row.raised_by,
        disposition=Disposition(row.disposition) if row.disposition else None,
        concession=row.concession,
        removal=row.removal,
        closed_on=row.closed_on,
        closed_by=row.closed_by or "",
    )


def write_back(row: NonconformanceRow, report: Report) -> None:
    row.disposition = str(report.disposition) if report.disposition else None
    row.concession = report.concession
    row.removal = report.removal
    row.closed_on = report.closed_on
    row.closed_by = report.closed_by or None


class NonconformanceRepository:
    """Raise, decide, close, and the lists somebody asks for."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def raise_report(self, report: Report) -> NonconformanceRow:
        if self.find(report.reference) is not None:
            raise ConflictError(
                "that reference is already on the register", reference=report.reference
            )
        row = NonconformanceRow(
            reference=report.reference,
            job=report.job,
            item=report.item,
            requirement=report.requirement,
            account=report.account,
            raised_on=report.raised_on,
            raised_by=report.raised_by,
        )
        write_back(row, report)
        self.session.add(row)
        return row

    def find(self, reference: str) -> NonconformanceRow | None:
        return self.session.scalars(
            select(NonconformanceRow).where(NonconformanceRow.reference == reference)
        ).first()

    def get(self, reference: str) -> NonconformanceRow:
        row = self.find(reference)
        if row is None:
            raise NotFoundError("non conformance report", reference)
        return row

    def report(self, reference: str) -> Report:
        return row_to_report(self.get(reference))

    def decide(
        self,
        reference: str,
        disposition: Disposition,
        concession: str = "",
        removal: str = "",
    ) -> Report:
        row = self.get(reference)
        report = row_to_report(row)
        report.decide(disposition, concession, removal)
        write_back(row, report)
        return report

    def close(self, reference: str, on: date, by: str) -> Report:
        row = self.get(reference)
        report = row_to_report(row)
        report.close(on, by)
        write_back(row, report)
        return report

    def for_job(self, job: str) -> tuple[Report, ...]:
        rows = self.session.scalars(
            select(NonconformanceRow)
            .where(NonconformanceRow.job == job)
            .order_by(NonconformanceRow.raised_on, NonconformanceRow.reference)
        ).all()
        return tuple(row_to_report(row) for row in rows)

    def open_reports(self) -> tuple[Report, ...]:
        rows = self.session.scalars(
            select(NonconformanceRow)
            .where(NonconformanceRow.closed_on.is_(None))
            .order_by(NonconformanceRow.raised_on, NonconformanceRow.reference)
        ).all()
        return tuple(row_to_report(row) for row in rows)

    def register_for(self, job: str) -> Register:
        """The domain register for one job, so the rules run against real rows."""
        register = Register()
        for report in self.for_job(job):
            register.reports[report.reference] = report
        return register
