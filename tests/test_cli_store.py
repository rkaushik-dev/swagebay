import pytest
from click.testing import CliRunner

from qu_imager.cli.main import cli, load_commands
from qu_imager.errors import ConflictError, ValidationError
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from test_reading_service import prepared

load_commands()

PLATE = b"a plate image, near enough for a hash"
OTHER = b"a different plate image entirely"


@pytest.fixture
def shop(tmp_path):
    url = f"sqlite:///{tmp_path / 'shop.sqlite3'}"
    engine = build_engine(url)
    create_schema(engine)
    made = unit_of_work_factory(engine)
    runner = CliRunner()

    def run(*args):
        return runner.invoke(cli, ["--database", url, *args], obj={}, standalone_mode=False)

    with transaction(made) as work:
        _, numbers = prepared(work, shots=2)
    run.numbers = numbers
    run.root = tmp_path
    return run


def a_file(shop, name, body=PLATE):
    path = shop.root / name
    path.write_bytes(body)
    return path


def ingest(shop, number, path, extra=()):
    return shop(
        "store",
        "ingest",
        number,
        str(path),
        "--width",
        "2048",
        "--height",
        "512",
        "--by",
        "0142",
        *extra,
    )


def test_a_file_is_taken_in_under_the_name_its_exposure_gives_it(shop):
    result = ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    assert result.exit_code == 0
    assert f"stored {shop.numbers[0]}.dcm" in result.output


def test_the_digest_printed_is_the_digest_of_the_bytes(shop):
    from qu_imager.imaging.store import digest_of

    result = ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    assert digest_of(PLATE)[:12] in result.output


def test_a_file_type_nothing_reads_is_refused(shop):
    result = ingest(shop, shop.numbers[0], a_file(shop, "scan.raw"))
    assert isinstance(result.exception, ValidationError)


def test_an_eight_bit_record_copy_is_stored_and_complained_about(shop):
    result = ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"), ("--bits", "8"))
    assert result.exit_code == 0
    assert "12 bit" in result.output


def test_a_viewing_copy_goes_in_as_a_revision(shop):
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    result = ingest(
        shop,
        shop.numbers[0],
        a_file(shop, "view.jpg", OTHER),
        ("--viewing", "--revision", "1", "--bits", "8"),
    )
    assert result.exit_code == 0
    assert f"{shop.numbers[0]}-v1.jpg" in result.output


def test_a_job_with_nothing_stored_says_so(shop):
    assert "no files stored" in shop("store", "list", "24-0001").output


def test_the_listing_names_every_file(shop):
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    ingest(shop, shop.numbers[1], a_file(shop, "second.dcm", OTHER))
    output = shop("store", "list", "24-0001").output
    assert f"{shop.numbers[0]}.dcm" in output
    assert f"{shop.numbers[1]}.dcm" in output


def test_the_listing_calls_out_an_exposure_with_no_record_copy(shop):
    ingest(
        shop,
        shop.numbers[0],
        a_file(shop, "view.jpg"),
        ("--viewing", "--revision", "1", "--bits", "8"),
    )
    assert "no record copy" in shop("store", "list", "24-0001").output


def test_a_store_that_matches_the_disk_verifies(shop, tmp_path):
    holding = tmp_path / "card"
    holding.mkdir()
    path = a_file(shop, "scan.dcm")
    ingest(shop, shop.numbers[0], path)
    (holding / f"{shop.numbers[0]}.dcm").write_bytes(PLATE)
    result = shop("store", "verify", "24-0001", str(holding))
    assert result.exit_code == 0
    assert "where it was" in result.output


def test_a_missing_file_fails_verification(shop, tmp_path):
    holding = tmp_path / "card"
    holding.mkdir()
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    result = shop("store", "verify", "24-0001", str(holding))
    assert result.exit_code == 4
    assert "1 missing" in result.output


def test_a_changed_file_fails_verification_and_says_so(shop, tmp_path):
    holding = tmp_path / "card"
    holding.mkdir()
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    (holding / f"{shop.numbers[0]}.dcm").write_bytes(OTHER)
    result = shop("store", "verify", "24-0001", str(holding))
    assert result.exit_code == 4
    assert "changed since ingest" in result.output


def test_a_stray_file_on_the_card_is_reported_apart(shop, tmp_path):
    holding = tmp_path / "card"
    holding.mkdir()
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    (holding / f"{shop.numbers[0]}.dcm").write_bytes(PLATE)
    (holding / "holiday.jpg").write_bytes(OTHER)
    result = shop("store", "verify", "24-0001", str(holding))
    assert result.exit_code == 4
    assert "not in the manifest" in result.output


def test_verifying_a_job_with_nothing_stored_against_an_empty_card_passes(shop, tmp_path):
    holding = tmp_path / "card"
    holding.mkdir()
    result = shop("store", "verify", "24-0001", str(holding))
    assert result.exit_code == 0


def test_ingesting_the_same_bytes_twice_is_not_an_error(shop):
    path = a_file(shop, "scan.dcm")
    ingest(shop, shop.numbers[0], path)
    assert ingest(shop, shop.numbers[0], path).exit_code == 0


def test_the_same_name_against_different_bytes_conflicts(shop):
    ingest(shop, shop.numbers[0], a_file(shop, "scan.dcm"))
    result = ingest(shop, shop.numbers[0], a_file(shop, "again.dcm", OTHER))
    assert isinstance(result.exception, ConflictError)
