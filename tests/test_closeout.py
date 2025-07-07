from datetime import date, datetime, timedelta

import pytest

from qu_imager.errors import ConflictError
from qu_imager.imaging.store import Format, Purpose, StoredImage, digest_of, expected_name
from qu_imager.nonconformance import Disposition, Report
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.services.closeout import checklist
from qu_imager.services.jobs import close_job
from qu_imager.services.reading import ReadingRecord, record_reading
from test_reading_service import prepared

from conftest import a_reader, database

READ = datetime(2024, 9, 25, 8, 0)
CLOSED = date(2024, 9, 30)
RAISED = date(2024, 9, 26)
HASH = digest_of(b"plate one")
OTHER = digest_of(b"plate two")


def a_report(reference="NCR-1", job="24-0001"):
    return Report(
        reference=reference,
        job=job,
        item="GW-104",
        requirement="API 1104 9.3.8",
        account="elongated slag",
        raised_on=RAISED,
        raised_by="0142",
    )


def an_image(number, revision=0, image_format=Format.DICONDE, purpose=Purpose.RECORD, bits=16):
    return StoredImage(
        radiograph_number=number,
        filename=expected_name(number, image_format, revision),
        image_format=image_format,
        bit_depth=bits,
        width=2048,
        height=512,
        byte_size=512,
        digest=HASH,
        captured_at=READ,
        purpose=purpose,
    )


def finished(work):
    job, numbers = prepared(work)
    record_reading(work, ReadingRecord(numbers[0], a_reader(), READ))
    return job, numbers


def test_a_finished_job_is_clear():
    with transaction(database()) as work:
        job, _ = finished(work)
        found = checklist(work, job)
    assert found.is_clear
    assert "ready to close" in found.describe()


def test_an_unread_film_shows_up_as_work():
    with transaction(database()) as work:
        job, _ = prepared(work)
        found = checklist(work, job)
    assert any("has not been read" in reason for reason in found.work_reasons)
    assert found.report_reasons == ()


def test_an_open_report_shows_up_apart_from_the_work():
    with transaction(database()) as work:
        job, _ = finished(work)
        work.nonconformances.raise_report(a_report())
        found = checklist(work, job)
    assert found.work_reasons == ()
    assert found.report_reasons == ("NCR-1 is still open",)
    assert not found.is_clear


def test_a_closed_report_does_not_block():
    with transaction(database()) as work:
        job, _ = finished(work)
        work.nonconformances.raise_report(a_report())
        work.nonconformances.decide("NCR-1", Disposition.REWORK)
        work.nonconformances.close("NCR-1", RAISED + timedelta(days=1), "0303")
        assert checklist(work, job).is_clear


def test_another_jobs_report_does_not_block_this_one():
    with transaction(database()) as work:
        job, _ = finished(work)
        work.nonconformances.raise_report(a_report(job="24-0099"))
        assert checklist(work, job).is_clear


def test_a_job_with_no_images_is_not_asked_about_images():
    with transaction(database()) as work:
        job, _ = finished(work)
        assert checklist(work, job).image_reasons == ()


def test_an_eight_bit_record_copy_shows_up_as_an_image_fault():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(an_image(numbers[0], bits=8), READ, "0142")
        found = checklist(work, job)
    assert any("12 bit" in reason for reason in found.image_reasons)


def test_an_exposure_with_only_a_viewing_copy_shows_up():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(
            an_image(numbers[0], revision=1, image_format=Format.JPEG, purpose=Purpose.VIEWING),
            READ,
            "0142",
        )
        found = checklist(work, job)
    assert any("no record copy" in reason for reason in found.image_reasons)


def test_a_file_missing_from_the_disk_shows_up_when_the_disk_is_offered():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(an_image(numbers[0]), READ, "0142")
        found = checklist(work, job, expected_files={})
    assert any("is missing from the store" in reason for reason in found.image_reasons)


def test_a_changed_file_shows_up_as_changed():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(an_image(numbers[0]), READ, "0142")
        name = expected_name(numbers[0], Format.DICONDE)
        found = checklist(work, job, expected_files={name: OTHER})
    assert any("has changed since ingest" in reason for reason in found.image_reasons)


def test_a_stray_file_shows_up_apart_from_a_missing_one():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(an_image(numbers[0]), READ, "0142")
        name = expected_name(numbers[0], Format.DICONDE)
        found = checklist(work, job, expected_files={name: HASH, "holiday.jpg": OTHER})
    assert any("not in the manifest" in reason for reason in found.image_reasons)


def test_a_matching_disk_leaves_the_job_clear():
    with transaction(database()) as work:
        job, numbers = finished(work)
        work.store.ingest(an_image(numbers[0]), READ, "0142")
        name = expected_name(numbers[0], Format.DICONDE)
        assert checklist(work, job, expected_files={name: HASH}).is_clear


def test_closing_refuses_on_an_open_report_and_names_it():
    with transaction(database()) as work:
        job, _ = finished(work)
        work.nonconformances.raise_report(a_report())
        with pytest.raises(ConflictError) as caught:
            close_job(work, job, CLOSED)
    assert "NCR-1 is still open" in caught.value.context["reasons"]


def test_closing_a_clear_job_works():
    with transaction(database()) as work:
        job, _ = finished(work)
        close_job(work, job, CLOSED)
        assert work.jobs.get(job).closed_on == CLOSED
