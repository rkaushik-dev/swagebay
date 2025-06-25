from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import NotFoundError, ValidationError
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.services.sources import Booking, register_device, register_source, send_out
from qu_imager.services.wipes import (
    history,
    record_wipe,
    wipe_due_on,
    wipes_overdue,
)
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import SealedSource, SourceCertificate

from conftest import database, pass_inspections

ASSAY = datetime(2024, 10, 1)
WIPED = datetime(2024, 10, 15, 9, 0)


def stocked(work, serial="IR-5512"):
    register_source(
        work,
        SealedSource(
            certificate=SourceCertificate(
                serial=serial,
                isotope=IR192,
                assay_activity=curie(90),
                assay_at=ASSAY,
                manufacturer="QSA",
                model="A424-9",
                active_diameter=mm(3),
                active_length=mm(3),
            ),
            working_floor=curie(15),
        ),
    )
    if work.sources.find_device("880D-221") is None:
        register_device(work, "880D-221", "880d")


def test_a_passing_wipe_is_recorded_and_sets_the_next_due_date():
    made = database()
    with transaction(made) as work:
        stocked(work)
        result = record_wipe(work, "IR-5512", WIPED, Decimal(0), "rp")
        assert result.passed
        assert result.expires_on == (WIPED + timedelta(days=180)).date()
        assert result.source_state == "in service"


def test_a_failing_wipe_takes_the_source_out_of_service_at_once():
    made = database()
    with transaction(made) as work:
        stocked(work)
        result = record_wipe(work, "IR-5512", WIPED, Decimal(400), "rp")
        assert not result.passed
        assert result.source_state == "awaiting disposal"
        assert work.sources.get("IR-5512").state == "awaiting disposal"


def test_the_limit_is_where_the_regulation_puts_it():
    made = database()
    with transaction(made) as work:
        stocked(work)
        assert record_wipe(work, "IR-5512", WIPED, Decimal(184), "rp").passed
    with transaction(made) as work:
        stocked(work, "IR-6000")
        assert not record_wipe(work, "IR-6000", WIPED, Decimal(185), "rp").passed


def test_a_wipe_needs_a_signature_and_a_believable_result():
    made = database()
    with transaction(made) as work:
        stocked(work)
        with pytest.raises(ValidationError):
            record_wipe(work, "IR-5512", WIPED, Decimal(0), "  ")
        with pytest.raises(ValidationError):
            record_wipe(work, "IR-5512", WIPED, Decimal(-1), "rp")


def test_a_wipe_against_a_source_that_is_not_there_is_refused():
    made = database()
    with pytest.raises(NotFoundError), transaction(made) as work:
        record_wipe(work, "IR-9999", WIPED, Decimal(0), "rp")


def test_a_source_never_wiped_has_no_due_date():
    made = database()
    with transaction(made) as work:
        stocked(work)
        assert wipe_due_on(work, "IR-5512") is None


def test_the_latest_passing_wipe_sets_the_due_date():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(0), "rp")
        later = WIPED + timedelta(days=100)
        record_wipe(work, "IR-5512", later, Decimal(1), "rp")
        assert wipe_due_on(work, "IR-5512") == (later + timedelta(days=180)).date()


def test_a_failing_wipe_does_not_set_a_due_date():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(400), "rp")
        assert wipe_due_on(work, "IR-5512") is None


def test_a_source_never_wiped_is_overdue_from_the_start():
    made = database()
    with transaction(made) as work:
        stocked(work)
        assert wipes_overdue(work, date(2024, 10, 2)) == ("IR-5512",)


def test_a_wiped_source_drops_off_the_overdue_list_and_comes_back_later():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(0), "rp")
        assert wipes_overdue(work, date(2024, 12, 1)) == ()
        assert wipes_overdue(work, date(2025, 6, 1)) == ("IR-5512",)


def test_a_source_out_of_service_is_not_chased_for_a_wipe():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(400), "rp")
        assert wipes_overdue(work, date(2025, 6, 1)) == ()


def test_the_history_starts_with_the_assay():
    made = database()
    with transaction(made) as work:
        stocked(work)
        sheet = history(work, "IR-5512")
        assert sheet[0].what == "assay"
        assert "90.0 Ci" in sheet[0].detail


def test_the_history_reads_forwards():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(0), "rp")
        pass_inspections(work, "880D-221", date(2024, 10, 20))
        send_out(
            work,
            Booking("IR-5512", "880D-221", "0142", "Come By Chance", date(2024, 10, 20)),
        )
        sheet = history(work, "IR-5512")
        assert [e.what for e in sheet] == ["assay", "leak test", "sent out"]
        assert [e.at for e in sheet] == sorted(e.at for e in sheet)


def test_the_history_says_whether_each_wipe_passed():
    made = database()
    with transaction(made) as work:
        stocked(work)
        record_wipe(work, "IR-5512", WIPED, Decimal(400), "rp")
        line = next(e for e in history(work, "IR-5512") if e.what == "leak test")
        assert "failed" in line.detail
        assert "400 Bq removed by rp" in line.detail


def test_the_result_prints_the_way_a_note_to_the_crew_reads():
    made = database()
    with transaction(made) as work:
        stocked(work)
        passed = record_wipe(work, "IR-5512", WIPED, Decimal(0), "rp")
        assert passed.describe() == "IR-5512 passed, current until 2025-04-13"
    with transaction(made) as work:
        stocked(work, "IR-6000")
        failed = record_wipe(work, "IR-6000", WIPED, Decimal(400), "rp")
        assert failed.describe() == "IR-6000 failed, source is now awaiting disposal"
