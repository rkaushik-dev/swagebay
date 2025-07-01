from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.nonconformance import Disposition, Report
from qu_imager.persistence.unit_of_work import UnitOfWork, transaction

from conftest import database

RAISED = date(2025, 6, 30)
LATER = RAISED + timedelta(days=5)


def a_report(reference="NCR-1", job="24-0001", by="0142"):
    return Report(
        reference=reference,
        job=job,
        item="GW-104",
        requirement="API 1104 section 9.3.8",
        account="elongated slag 32 mm in a 300 mm length",
        raised_on=RAISED,
        raised_by=by,
    )


def test_a_raised_report_comes_back_undecided():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        found = work.nonconformances.report("NCR-1")
    assert found.is_open
    assert found.disposition is None
    assert found.raised_by == "0142"


def test_the_same_reference_twice_is_a_conflict():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        with pytest.raises(ConflictError):
            work.nonconformances.raise_report(a_report())


def test_a_reference_nobody_raised_is_a_not_found():
    with UnitOfWork(database()) as work:
        assert work.nonconformances.find("NCR-9") is None
        with pytest.raises(NotFoundError):
            work.nonconformances.get("NCR-9")


def test_a_decision_is_written_back():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REWORK)
        assert work.nonconformances.report("NCR-1").disposition is Disposition.REWORK


def test_use_as_is_without_a_concession_is_still_refused_through_the_repository():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        with pytest.raises(ConflictError):
            work.nonconformances.decide("NCR-1", Disposition.USE_AS_IS)


def test_a_concession_survives_the_round_trip():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.USE_AS_IS, concession="CON-88")
        assert work.nonconformances.report("NCR-1").concession == "CON-88"


def test_a_removal_reference_survives_the_round_trip():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REJECT, removal="SCRAP-14")
        assert work.nonconformances.report("NCR-1").removal == "SCRAP-14"


def test_closing_writes_the_date_and_the_badge():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REWORK)
        work.nonconformances.close("NCR-1", LATER, "0303")
        found = work.nonconformances.report("NCR-1")
    assert not found.is_open
    assert found.closed_by == "0303"
    assert found.closed_on == LATER


def test_the_author_still_cannot_close_it_through_the_repository():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REWORK)
        with pytest.raises(ConflictError):
            work.nonconformances.close("NCR-1", LATER, "0142")


def test_one_jobs_reports_read_in_the_order_they_were_raised():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report("NCR-2"))
        work.nonconformances.raise_report(a_report("NCR-1"))
        work.nonconformances.raise_report(a_report("NCR-3", job="24-0002"))
        assert [r.reference for r in work.nonconformances.for_job("24-0001")] == ["NCR-1", "NCR-2"]


def test_the_open_list_spans_every_job():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report("NCR-1"))
        work.nonconformances.raise_report(a_report("NCR-2", job="24-0002"))
        work.nonconformances.decide("NCR-1", Disposition.REWORK)
        work.nonconformances.close("NCR-1", LATER, "0303")
        assert [r.reference for r in work.nonconformances.open_reports()] == ["NCR-2"]


def test_the_register_for_a_job_answers_the_domain_questions():
    with UnitOfWork(database()) as work:
        work.nonconformances.raise_report(a_report())
        register = work.nonconformances.register_for("24-0001")
    assert register.blocks_closing("24-0001") == ("NCR-1 is still open",)
    assert len(register.awaiting_a_decision()) == 1


def test_a_report_survives_the_transaction_that_raised_it():
    made = database()
    with transaction(made) as work:
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REPAIR, concession="CON-9")
    with UnitOfWork(made) as work:
        found = work.nonconformances.report("NCR-1")
    assert found.disposition is Disposition.REPAIR
    assert found.concession == "CON-9"
