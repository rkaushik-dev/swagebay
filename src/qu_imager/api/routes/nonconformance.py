"""Non conformance over http.

Raising is a post to the collection. Deciding and closing are posts to their
own paths rather than a patch on the report, because both are events with
their own preconditions and a patch that accepts any subset of the fields would
let a caller set closed_on without ever deciding anything.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, status

from qu_imager.api.app import Work
from qu_imager.api.schemas import CloseIn, DecisionIn, ReportIn, ReportOut
from qu_imager.errors import ValidationError
from qu_imager.nonconformance import Disposition, Report

router = APIRouter(prefix="/nonconformances", tags=["nonconformance"])


def _disposition(key: str) -> Disposition:
    try:
        return Disposition[key.strip().upper().replace(" ", "_").replace("-", "_")]
    except KeyError:
        known = ", ".join(d.name.lower() for d in Disposition)
        raise ValidationError(
            "nothing here dispositions like that", given=key, known=known
        ) from None


def _out(report: Report, on: date) -> ReportOut:
    return ReportOut(
        reference=report.reference,
        job=report.job,
        item=report.item,
        requirement=report.requirement,
        disposition=str(report.disposition) if report.disposition else None,
        concession=report.concession,
        removal=report.removal,
        raised_on=report.raised_on,
        raised_by=report.raised_by,
        closed_on=report.closed_on,
        closed_by=report.closed_by or None,
        days_open=report.days_open(on),
        overdue=report.is_overdue(on),
    )


@router.post("", status_code=status.HTTP_201_CREATED)
def raise_report(body: ReportIn, work: Work) -> ReportOut:
    report = Report(
        reference=body.reference,
        job=body.job,
        item=body.item,
        requirement=body.requirement,
        account=body.account,
        raised_on=body.raised_on,
        raised_by=body.raised_by,
    )
    work.nonconformances.raise_report(report)
    return _out(report, body.raised_on)


@router.get("")
def list_open(work: Work, on: date | None = None) -> list[ReportOut]:
    day = on or date.today()
    return [_out(r, day) for r in work.nonconformances.open_reports()]


@router.get("/{reference}")
def read_report(reference: str, work: Work, on: date | None = None) -> ReportOut:
    report = work.nonconformances.report(reference)
    return _out(report, on or date.today())


@router.post("/{reference}/decision")
def decide(reference: str, body: DecisionIn, work: Work) -> ReportOut:
    report = work.nonconformances.decide(
        reference, _disposition(body.disposition), body.concession, body.removal
    )
    return _out(report, date.today())


@router.post("/{reference}/closure")
def close(reference: str, body: CloseIn, work: Work) -> ReportOut:
    report = work.nonconformances.close(reference, body.closed_on, body.closed_by)
    return _out(report, body.closed_on)
