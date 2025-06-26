from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.nonconformance import (
    ESCALATE_AFTER_DAYS,
    Disposition,
    Register,
    Report,
)

RAISED = date(2025, 6, 26)
LATER = RAISED + timedelta(days=4)


def a_report(reference="NCR-1", job="24-0001", item="GW-104", by="0142"):
    return Report(
        reference=reference,
        job=job,
        item=item,
        requirement="API 1104 section 9.3.8",
        account="elongated slag 32 mm in a 300 mm length",
        raised_on=RAISED,
        raised_by=by,
    )


def test_a_report_opens_undecided_and_open():
    report = a_report()
    assert report.is_open
    assert report.disposition is None
    assert "undecided" in report.describe()


def test_every_field_on_the_form_has_to_be_filled_in():
    for field_name in ("reference", "job", "item", "requirement", "account", "raised_by"):
        blanks = {
            "reference": "NCR-1",
            "job": "24-0001",
            "item": "GW-104",
            "requirement": "API 1104",
            "account": "slag",
            "raised_by": "0142",
        }
        blanks[field_name] = "   "
        with pytest.raises(ValidationError) as caught:
            Report(raised_on=RAISED, **blanks)
        assert caught.value.context["field"] == field_name


def test_rework_needs_nothing_beyond_the_decision():
    report = a_report()
    report.decide(Disposition.REWORK)
    assert report.disposition is Disposition.REWORK


def test_use_as_is_without_a_concession_is_refused():
    with pytest.raises(ConflictError) as caught:
        a_report().decide(Disposition.USE_AS_IS)
    assert caught.value.context["disposition"] == "use as is"


def test_use_as_is_with_a_concession_is_accepted():
    report = a_report()
    report.decide(Disposition.USE_AS_IS, concession="CON-88")
    assert report.concession == "CON-88"


def test_a_repair_also_needs_the_client_to_agree():
    with pytest.raises(ConflictError):
        a_report().decide(Disposition.REPAIR)
    report = a_report()
    report.decide(Disposition.REPAIR, concession="CON-89")
    assert report.disposition is Disposition.REPAIR


def test_a_rejected_item_needs_somewhere_to_have_gone():
    with pytest.raises(ConflictError):
        a_report().decide(Disposition.REJECT)
    report = a_report()
    report.decide(Disposition.REJECT, removal="SCRAP-14")
    assert report.removal == "SCRAP-14"


def test_only_two_dispositions_need_a_concession():
    needing = {d for d in Disposition if d.needs_concession}
    assert needing == {Disposition.USE_AS_IS, Disposition.REPAIR}


def test_a_report_with_nothing_decided_cannot_be_closed():
    with pytest.raises(ConflictError):
        a_report().close(LATER, "0303")


def test_the_person_who_raised_it_cannot_close_it():
    report = a_report(by="0142")
    report.decide(Disposition.REWORK)
    with pytest.raises(ConflictError) as caught:
        report.close(LATER, "0142")
    assert caught.value.context["badge"] == "0142"


def test_an_unsigned_close_is_refused():
    report = a_report()
    report.decide(Disposition.REWORK)
    with pytest.raises(ValidationError):
        report.close(LATER, "   ")


def test_a_close_dated_before_the_raise_is_refused():
    report = a_report()
    report.decide(Disposition.REWORK)
    with pytest.raises(ValidationError):
        report.close(RAISED - timedelta(days=1), "0303")


def test_a_closed_report_reads_as_closed():
    report = a_report()
    report.decide(Disposition.REWORK)
    report.close(LATER, "0303")
    assert not report.is_open
    assert "closed 2025-06-30 by 0303" in report.describe()


def test_a_closed_report_cannot_be_closed_again_or_redecided():
    report = a_report()
    report.decide(Disposition.REWORK)
    report.close(LATER, "0303")
    with pytest.raises(ConflictError):
        report.close(LATER, "0303")
    with pytest.raises(ConflictError):
        report.decide(Disposition.REJECT, removal="SCRAP-1")


def test_the_clock_stops_when_it_closes():
    report = a_report()
    report.decide(Disposition.REWORK)
    report.close(LATER, "0303")
    much_later = RAISED + timedelta(days=90)
    assert report.days_open(much_later) == 4
    assert not report.is_overdue(much_later)


def test_a_report_left_open_a_month_escalates():
    report = a_report()
    just_inside = RAISED + timedelta(days=ESCALATE_AFTER_DAYS)
    assert not report.is_overdue(just_inside)
    assert report.is_overdue(just_inside + timedelta(days=1))
    assert report.escalates_on() == just_inside + timedelta(days=1)


def test_the_register_refuses_the_same_reference_twice():
    register = Register()
    register.raise_report(a_report())
    with pytest.raises(ConflictError):
        register.raise_report(a_report())
    assert len(register) == 1


def test_a_reference_nobody_raised_is_refused():
    with pytest.raises(ConflictError):
        Register().get("NCR-9")


def test_one_jobs_reports_read_in_the_order_they_were_raised():
    register = Register()
    register.raise_report(a_report("NCR-2"))
    register.raise_report(a_report("NCR-1"))
    register.raise_report(a_report("NCR-3", job="24-0002"))
    assert [r.reference for r in register.for_job("24-0001")] == ["NCR-1", "NCR-2"]


def test_the_open_list_drops_a_report_when_it_closes():
    register = Register()
    register.raise_report(a_report())
    assert len(register.open_reports()) == 1
    report = register.get("NCR-1")
    report.decide(Disposition.REWORK)
    report.close(LATER, "0303")
    assert register.open_reports() == ()


def test_the_overdue_list_is_the_open_ones_past_the_month():
    register = Register()
    register.raise_report(a_report())
    assert register.overdue_on(RAISED + timedelta(days=10)) == ()
    assert len(register.overdue_on(RAISED + timedelta(days=40))) == 1


def test_the_undecided_list_is_what_the_monday_meeting_is_about():
    register = Register()
    register.raise_report(a_report("NCR-1"))
    register.raise_report(a_report("NCR-2"))
    register.get("NCR-2").decide(Disposition.REWORK)
    assert [r.reference for r in register.awaiting_a_decision()] == ["NCR-1"]


def test_the_dispositions_are_counted_for_the_quarterly_review():
    register = Register()
    for reference, disposition, extra in (
        ("NCR-1", Disposition.REWORK, {}),
        ("NCR-2", Disposition.REWORK, {}),
        ("NCR-3", Disposition.USE_AS_IS, {"concession": "CON-1"}),
    ):
        register.raise_report(a_report(reference))
        register.get(reference).decide(disposition, **extra)
    assert register.by_disposition() == {"rework": 2, "use as is": 1}


def test_every_concession_the_client_signed_can_be_listed():
    register = Register()
    register.raise_report(a_report("NCR-1"))
    register.raise_report(a_report("NCR-2"))
    register.get("NCR-1").decide(Disposition.USE_AS_IS, concession="CON-1")
    register.get("NCR-2").decide(Disposition.REPAIR, concession="CON-2")
    assert register.concessions_on("24-0001") == ("CON-1", "CON-2")


def test_an_open_report_stops_the_job_being_signed_off():
    register = Register()
    register.raise_report(a_report())
    assert register.blocks_closing("24-0001") == ("NCR-1 is still open",)


def test_a_job_with_everything_closed_is_not_blocked():
    register = Register()
    register.raise_report(a_report())
    report = register.get("NCR-1")
    report.decide(Disposition.REWORK)
    report.close(LATER, "0303")
    assert register.blocks_closing("24-0001") == ()


def test_another_jobs_reports_do_not_block_this_one():
    register = Register()
    register.raise_report(a_report("NCR-1", job="24-0002"))
    assert register.blocks_closing("24-0001") == ()
