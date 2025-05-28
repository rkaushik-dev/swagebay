from datetime import datetime

import pytest

from qu_imager.errors import ValidationError
from qu_imager.imaging.store import (
    Format,
    Manifest,
    Purpose,
    StoredImage,
    digest_of,
    expected_name,
    faults,
    is_admissible_file,
)

CAPTURED = datetime(2025, 5, 27, 10, 0)
BYTES = b"not really a radiograph"
HASH = digest_of(BYTES)
OTHER = digest_of(b"different bytes entirely")


def stored(
    number="24-0001-R0001",
    image_format=Format.DICONDE,
    bit_depth=16,
    revision=0,
    purpose=Purpose.RECORD,
    digest=HASH,
):
    return StoredImage(
        radiograph_number=number,
        filename=expected_name(number, image_format, revision),
        image_format=image_format,
        bit_depth=bit_depth,
        width=2048,
        height=512,
        byte_size=len(BYTES),
        digest=digest,
        captured_at=CAPTURED,
        purpose=purpose,
    )


def test_the_name_comes_from_the_radiograph_number():
    assert expected_name("24-0001-R0001", Format.TIFF) == "24-0001-R0001.tif"


def test_a_lower_case_number_still_names_the_same_file():
    assert expected_name("24-0001-r0001", Format.TIFF) == "24-0001-R0001.tif"


def test_a_rescan_is_a_revision_not_a_new_number():
    assert expected_name("24-0001-R0001", Format.TIFF, 2) == "24-0001-R0001-v2.tif"


def test_a_name_with_no_radiograph_behind_it_is_refused():
    with pytest.raises(ValidationError):
        expected_name("   ", Format.TIFF)


def test_a_negative_revision_is_refused():
    with pytest.raises(ValidationError):
        expected_name("24-0001-R0001", Format.TIFF, -1)


def test_a_file_named_something_nobody_can_derive_is_refused():
    with pytest.raises(ValidationError) as caught:
        StoredImage(
            radiograph_number="24-0001-R0001",
            filename="scan_final_FINAL.tif",
            image_format=Format.TIFF,
            bit_depth=16,
            width=100,
            height=100,
            byte_size=10,
            digest=HASH,
            captured_at=CAPTURED,
        )
    assert caught.value.context["filename"] == "scan_final_FINAL.tif"


def test_a_digest_that_is_not_a_digest_is_refused():
    with pytest.raises(ValidationError):
        stored(digest="deadbeef")


def test_a_bit_depth_nobody_ships_is_refused():
    with pytest.raises(ValidationError):
        stored(bit_depth=10)


def test_an_empty_file_is_not_an_image():
    with pytest.raises(ValidationError):
        StoredImage(
            radiograph_number="24-0001-R0001",
            filename="24-0001-R0001.tif",
            image_format=Format.TIFF,
            bit_depth=16,
            width=10,
            height=10,
            byte_size=0,
            digest=HASH,
            captured_at=CAPTURED,
        )


def test_an_image_with_no_height_is_refused():
    with pytest.raises(ValidationError):
        StoredImage(
            radiograph_number="24-0001-R0001",
            filename="24-0001-R0001.tif",
            image_format=Format.TIFF,
            bit_depth=16,
            width=10,
            height=0,
            byte_size=10,
            digest=HASH,
            captured_at=CAPTURED,
        )


def test_the_revision_reads_back_off_the_name():
    assert stored(revision=3).revision == 3
    assert stored().revision == 0


def test_a_lossless_record_copy_has_nothing_wrong_with_it():
    assert faults(stored()) == ()
    assert is_admissible_file(stored())


def test_a_jpeg_cannot_be_the_record():
    reasons = faults(stored(image_format=Format.JPEG, bit_depth=8))
    assert any("lossy" in reason for reason in reasons)


def test_eight_bit_is_below_what_an_evaluation_is_made_at():
    reasons = faults(stored(bit_depth=8))
    assert any("12 bit" in reason for reason in reasons)


def test_a_viewing_copy_that_claims_to_be_the_first_file_is_wrong():
    reasons = faults(stored(purpose=Purpose.VIEWING, revision=0))
    assert any("viewing copy" in reason for reason in reasons)


def test_a_viewing_copy_as_a_revision_is_fine_even_lossy():
    image = stored(image_format=Format.JPEG, bit_depth=8, revision=1, purpose=Purpose.VIEWING)
    assert faults(image) == ()


def test_only_diconde_has_somewhere_to_put_the_technique():
    assert Format.DICONDE.carries_technique
    assert not Format.TIFF.carries_technique


def test_pixels_are_the_two_dimensions():
    assert stored().pixels == 2048 * 512


def test_a_manifest_holds_what_was_added():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    assert len(manifest) == 1
    assert "24-0001-R0001.dcm" in manifest


def test_adding_the_same_file_twice_is_not_a_problem():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    manifest.add(stored())
    assert len(manifest) == 1


def test_the_same_name_against_different_bytes_is_refused():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    with pytest.raises(ValidationError):
        manifest.add(stored(digest=OTHER))


def test_revisions_of_one_exposure_come_back_in_order():
    manifest = Manifest(job="24-0001")
    manifest.add(stored(revision=2, purpose=Purpose.VIEWING))
    manifest.add(stored())
    assert [i.revision for i in manifest.for_radiograph("24-0001-R0001")] == [0, 2]


def test_another_exposure_is_not_in_this_ones_list():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    manifest.add(stored(number="24-0001-R0002"))
    assert len(manifest.for_radiograph("24-0001-R0001")) == 1


def test_the_record_copy_is_the_one_that_is_not_a_viewing_copy():
    manifest = Manifest(job="24-0001")
    manifest.add(stored(revision=1, purpose=Purpose.VIEWING))
    manifest.add(stored())
    assert manifest.record_copy("24-0001-R0001").revision == 0


def test_an_exposure_with_only_viewing_copies_has_no_record():
    manifest = Manifest(job="24-0001")
    manifest.add(stored(revision=1, purpose=Purpose.VIEWING))
    assert manifest.record_copy("24-0001-R0001") is None
    assert manifest.radiographs_without_a_record_copy() == ("24-0001-R0001",)


def test_a_store_that_matches_the_manifest_is_clean():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    found = manifest.verify({"24-0001-R0001.dcm": HASH})
    assert found.is_clean
    assert "where it was" in found.describe()


def test_a_file_that_is_gone_reads_as_missing():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    found = manifest.verify({})
    assert found.missing == ("24-0001-R0001.dcm",)
    assert found.unexpected == ()
    assert found.altered == ()


def test_a_file_nobody_expected_is_not_a_missing_file():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    found = manifest.verify({"24-0001-R0001.dcm": HASH, "holiday.jpg": OTHER})
    assert found.unexpected == ("holiday.jpg",)
    assert found.missing == ()


def test_a_file_resaved_through_a_viewer_reads_as_altered():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    found = manifest.verify({"24-0001-R0001.dcm": OTHER})
    assert found.altered == ("24-0001-R0001.dcm",)
    assert found.missing == ()


def test_the_three_causes_are_counted_apart():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    manifest.add(stored(number="24-0001-R0002"))
    found = manifest.verify({"24-0001-R0002.dcm": OTHER, "stray.tif": HASH})
    assert found.missing == ("24-0001-R0001.dcm",)
    assert found.altered == ("24-0001-R0002.dcm",)
    assert found.unexpected == ("stray.tif",)
    assert not found.is_clean
    assert found.describe() == "1 missing, 1 not in the manifest, 1 changed since ingest"


def test_manifest_faults_name_the_file_they_are_about():
    manifest = Manifest(job="24-0001")
    manifest.add(stored(bit_depth=8))
    assert manifest.faults()[0].startswith("24-0001-R0001.dcm: 8 bit")


def test_a_clean_manifest_reports_no_faults():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    assert manifest.faults() == ()


def test_the_manifest_adds_up_what_the_store_holds():
    manifest = Manifest(job="24-0001")
    manifest.add(stored())
    manifest.add(stored(number="24-0001-R0002"))
    assert manifest.total_bytes() == 2 * len(BYTES)


def test_the_same_bytes_always_hash_the_same_way():
    assert digest_of(BYTES) == digest_of(BYTES)
    assert digest_of(BYTES) != digest_of(BYTES + b" ")
