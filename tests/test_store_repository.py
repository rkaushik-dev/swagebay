from datetime import datetime

import pytest

from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.identity import JobNumber
from qu_imager.imaging.store import Format, Purpose, StoredImage, digest_of, expected_name
from qu_imager.persistence.unit_of_work import transaction
from test_reading_service import prepared

from conftest import database

CAPTURED = datetime(2024, 9, 24, 23, 40)
INGESTED = datetime(2024, 9, 25, 7, 0)
HASH = digest_of(b"plate one")
OTHER = digest_of(b"plate two")


def an_image(number, revision=0, image_format=Format.DICONDE, purpose=Purpose.RECORD, digest=HASH):
    return StoredImage(
        radiograph_number=number,
        filename=expected_name(number, image_format, revision),
        image_format=image_format,
        bit_depth=16,
        width=2048,
        height=512,
        byte_size=512,
        digest=digest,
        captured_at=CAPTURED,
        purpose=purpose,
    )


def test_an_ingested_file_comes_back_against_its_exposure():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        found = work.store.for_radiograph(numbers[0])
    assert len(found) == 1
    assert found[0].digest == HASH


def test_the_same_file_ingested_twice_lands_once():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        assert len(work.store.for_radiograph(numbers[0])) == 1


def test_the_same_name_against_different_bytes_is_a_conflict():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        with pytest.raises(ConflictError):
            work.store.ingest(an_image(numbers[0], digest=OTHER), INGESTED, "0142")


def test_a_file_against_an_exposure_nobody_shot_is_refused():
    with transaction(database()) as work:
        prepared(work)
        with pytest.raises(NotFoundError):
            work.store.ingest(an_image("24-0001-R9999"), INGESTED, "0142")


def test_revisions_come_back_in_order():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(
            an_image(numbers[0], revision=1, image_format=Format.JPEG, purpose=Purpose.VIEWING),
            INGESTED,
            "0142",
        )
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        assert [i.revision for i in work.store.for_radiograph(numbers[0])] == [0, 1]


def test_who_ingested_it_and_when_is_on_the_row():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        row = work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        work.flush()
        assert row.ingested_by == "0142"
        assert row.ingested_at == INGESTED


def test_a_file_the_store_does_not_hold_is_a_not_found():
    with transaction(database()) as work:
        prepared(work)
        assert work.store.find("nothing.tif") is None
        with pytest.raises(NotFoundError):
            work.store.get("nothing.tif")


def test_the_manifest_gathers_every_file_on_the_job():
    with transaction(database()) as work:
        job, numbers = prepared(work, shots=2)
        for number in numbers:
            work.store.ingest(an_image(number), INGESTED, "0142")
        manifest = work.store.manifest_for(job)
    assert len(manifest) == 2
    assert manifest.job == "24-0001"


def test_a_job_with_no_files_has_an_empty_manifest():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        assert len(work.store.manifest_for(job)) == 0


def test_another_jobs_files_are_not_in_this_manifest():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        assert len(work.store.manifest_for(JobNumber(24, 99))) == 0


def test_the_manifest_verifies_against_what_is_on_the_disk():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
        manifest = work.store.manifest_for(job)
        name = expected_name(numbers[0], Format.DICONDE)
        assert manifest.verify({name: HASH}).is_clean
        assert manifest.verify({name: OTHER}).altered == (name,)


def test_the_manifest_finds_an_exposure_with_no_record_copy():
    with transaction(database()) as work:
        job, numbers = prepared(work)
        work.store.ingest(
            an_image(numbers[0], revision=1, image_format=Format.JPEG, purpose=Purpose.VIEWING),
            INGESTED,
            "0142",
        )
        manifest = work.store.manifest_for(job)
    assert manifest.radiographs_without_a_record_copy() == (numbers[0],)


def test_the_files_survive_the_transaction_that_ingested_them():
    made = database()
    with transaction(made) as work:
        job, numbers = prepared(work)
        work.store.ingest(an_image(numbers[0]), INGESTED, "0142")
    with transaction(made) as work:
        assert len(work.store.for_radiograph(numbers[0])) == 1
