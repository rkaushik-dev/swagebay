import csv
import io
from datetime import date

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.identity import JobNumber
from qu_imager.quantities.length import mm
from qu_imager.reporting.exports import (
    MANIFEST_COLUMNS,
    columns_of,
    manifest_to_text,
    sheet_named,
    sheets,
    to_files,
    to_text,
)
from qu_imager.reporting.package import Package, RadiographLine, WeldLine

OPENED = date(2024, 11, 1)


def a_package(complete=True, welds=None, radiographs=None):
    return Package(
        job=JobNumber(24, 417),
        client="Maritime Pipelines",
        site="Come By Chance",
        code=Code.API_1104,
        procedure_number="RT-001",
        opened_on=OPENED,
        closed_on=None,
        welds=welds
        if welds is not None
        else (
            WeldLine("GW-104", mm("10.97"), "w-19", 1, "accept", ()),
            WeldLine("GW-104R1", mm("10.97"), "w-19", 1, "repair", ("9.3.8",)),
        ),
        radiographs=radiographs
        if radiographs is not None
        else (
            RadiographLine(
                "24-0417-R0001", "GW-104", 1, OPENED, "RT-001 rev 1", "film", True, "0142", 0
            ),
        ),
        faults=() if complete else ("GW-110 has no radiographs",),
    )


def rows(text):
    return list(csv.reader(io.StringIO(text)))


def test_every_sheet_starts_with_its_header():
    for what in sheets():
        first = rows(to_text(a_package(), what))[0]
        assert tuple(first) == tuple(columns_of(what))


def test_the_weld_sheet_names_its_unit_in_the_column():
    assert "wall_mm" in sheet_named("welds").columns
    body = rows(to_text(a_package(), "welds"))
    assert body[1][sheet_named("welds").columns.index("wall_mm")] == "10.97"


def test_the_weld_sheet_carries_the_disposition_and_the_clauses():
    body = rows(to_text(a_package(), "welds"))
    assert body[1][sheet_named("welds").columns.index("disposition")] == "accept"
    assert body[2][sheet_named("welds").columns.index("clauses")] == "9.3.8"


def test_a_weld_with_no_clauses_leaves_the_cell_empty():
    body = rows(to_text(a_package(), "welds"))
    assert body[1][sheet_named("welds").columns.index("clauses")] == ""


def test_the_radiograph_sheet_writes_yes_and_no_rather_than_true_and_false():
    body = rows(to_text(a_package(), "radiographs"))
    assert body[1][sheet_named("radiographs").columns.index("admissible")] == "yes"


def test_an_unread_film_leaves_the_reader_cell_empty():
    unread = (
        RadiographLine("24-0417-R0002", "GW-104", 2, OPENED, "RT-001 rev 1", "film", True, None, 0),
    )
    body = rows(to_text(a_package(radiographs=unread), "radiographs"))
    assert body[1][sheet_named("radiographs").columns.index("read_by")] == ""


def test_the_summary_is_one_row_under_its_header():
    body = rows(to_text(a_package(), "summary"))
    assert len(body) == 2
    assert body[1][sheet_named("summary").columns.index("job")] == "24-0417"
    assert body[1][sheet_named("summary").columns.index("complete")] == "yes"


def test_an_incomplete_package_says_so_in_the_summary():
    body = rows(to_text(a_package(complete=False), "summary"))
    assert body[1][sheet_named("summary").columns.index("complete")] == "no"


def test_the_repair_rate_is_written_to_four_places():
    body = rows(to_text(a_package(), "summary"))
    assert body[1][sheet_named("summary").columns.index("repair_rate")] == "0.5000"


def test_an_open_job_leaves_the_close_date_empty():
    body = rows(to_text(a_package(), "summary"))
    assert body[1][sheet_named("summary").columns.index("closed_on")] == ""


def test_a_field_with_a_comma_in_it_is_quoted_properly():
    awkward = (WeldLine("GW-104", mm(10), "w-19, second shift", 1, "accept", ("9.3.1", "9.3.8")),)
    text = to_text(a_package(welds=awkward), "welds")
    assert '"w-19, second shift"' in text
    assert rows(text)[1][sheet_named("welds").columns.index("welder")] == "w-19, second shift"


def test_every_line_ends_the_same_way_whatever_the_platform_is():
    text = to_text(a_package(), "welds")
    assert "\r" not in text
    assert text.endswith("\n")


def test_an_export_nobody_publishes_is_refused():
    with pytest.raises(ValidationError) as caught:
        to_text(a_package(), "everything")
    assert "welds" in caught.value.context["known"]


def test_the_file_names_carry_the_job_number():
    files = to_files(a_package())
    assert set(files) == {
        "24-0417-welds.csv",
        "24-0417-radiographs.csv",
        "24-0417-summary.csv",
    }
    assert files["24-0417-summary.csv"].startswith("job,client")


def test_the_sheets_are_listed_in_a_stable_order():
    assert sheets() == ("radiographs", "summary", "welds")


def test_every_sheet_writes_its_own_header():
    package = a_package()
    for name in sheets():
        header = rows(to_text(package, name))[0]
        assert tuple(header) == tuple(columns_of(name))


def test_a_sheet_nobody_knows_about_names_the_ones_that_exist():
    with pytest.raises(ValidationError) as caught:
        sheet_named("gizmo")
    assert "welds" in caught.value.context["known"]


def test_the_sheet_list_and_the_lookup_cannot_drift():
    from qu_imager.reporting.exports import SHEETS

    assert sheets() == tuple(sorted(sheet.name for sheet in SHEETS))


def a_manifest():
    from datetime import datetime

    from qu_imager.imaging.store import (
        Format,
        Manifest,
        Purpose,
        StoredImage,
        digest_of,
        expected_name,
    )

    manifest = Manifest(job="24-0417")
    for number, revision, image_format, purpose in (
        ("24-0417-R0001", 0, Format.DICONDE, Purpose.RECORD),
        ("24-0417-R0001", 1, Format.JPEG, Purpose.VIEWING),
    ):
        manifest.add(
            StoredImage(
                radiograph_number=number,
                filename=expected_name(number, image_format, revision),
                image_format=image_format,
                bit_depth=16 if purpose is Purpose.RECORD else 8,
                width=2048,
                height=512,
                byte_size=1024,
                digest=digest_of(f"{number}-{revision}".encode()),
                captured_at=datetime(2024, 11, 3, 22, 0),
                purpose=purpose,
            )
        )
    return manifest


def test_the_manifest_writes_its_own_header():
    assert tuple(rows(manifest_to_text(a_manifest()))[0]) == MANIFEST_COLUMNS


def test_the_manifest_carries_the_digest_the_client_checks_against():
    body = rows(manifest_to_text(a_manifest()))
    assert len(body[1][MANIFEST_COLUMNS.index("sha256")]) == 64


def test_the_manifest_reads_in_file_name_order():
    body = rows(manifest_to_text(a_manifest()))
    names = [line[0] for line in body[1:]]
    assert names == sorted(names)


def test_the_manifest_says_which_copy_is_the_record():
    body = rows(manifest_to_text(a_manifest()))
    purposes = {line[0]: line[MANIFEST_COLUMNS.index("purpose")] for line in body[1:]}
    assert purposes["24-0417-R0001.dcm"] == "record"
    assert purposes["24-0417-R0001-v1.jpg"] == "viewing"


def test_an_empty_manifest_is_still_a_valid_file():
    from qu_imager.imaging.store import Manifest

    body = rows(manifest_to_text(Manifest(job="24-0417")))
    assert len(body) == 1
