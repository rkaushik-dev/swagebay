import csv
import io
from datetime import date

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.identity import JobNumber
from qu_imager.quantities.length import mm
from qu_imager.reporting.exports import (
    columns_of,
    sheet_named,
    sheets,
    to_files,
    to_text,
)
from qu_imager.reporting.package import Package, RadiographLine, WeldLine

OPENED = date(2024, 11, 1)


def a_package(complete=True, welds=None, radiographs=None, reports=(), files=()):
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
        reports=reports,
        files=files,
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


def test_a_rate_on_two_welds_is_left_blank_rather_than_stated():
    body = rows(to_text(a_package(), "summary"))
    assert body[1][sheet_named("summary").columns.index("repair_rate")] == ""
    assert body[1][sheet_named("summary").columns.index("repair_rate_basis")] == "1 of 2"


def test_a_rate_with_a_denominator_behind_it_is_written_to_four_places():
    welds = tuple(
        WeldLine(f"GW-{index:03d}", mm("10.97"), "w-19", 1, "accept", ()) for index in range(1, 41)
    )
    body = rows(to_text(a_package(welds=welds), "summary"))
    assert body[1][sheet_named("summary").columns.index("repair_rate")] == "0.0000"


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
        "24-0417-files.csv",
        "24-0417-radiographs.csv",
        "24-0417-reports.csv",
        "24-0417-summary.csv",
        "24-0417-welds.csv",
    }
    assert files["24-0417-summary.csv"].startswith("job,client")


def test_the_sheets_are_listed_in_a_stable_order():
    assert sheets() == ("files", "radiographs", "reports", "summary", "welds")


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


def a_report(reference="NCR-1", closed=False):
    from qu_imager.nonconformance import Disposition, Report

    report = Report(
        reference=reference,
        job="24-0417",
        item="GW-104",
        requirement="API 1104 9.3.8",
        account="elongated slag",
        raised_on=OPENED,
        raised_by="0142",
    )
    if closed:
        report.decide(Disposition.USE_AS_IS, concession="CON-1")
        report.close(date(2024, 11, 5), "0303")
    return report


def a_file(number="24-0417-R0001", revision=0):
    from datetime import datetime

    from qu_imager.imaging.store import (
        Format,
        Purpose,
        StoredImage,
        digest_of,
        expected_name,
    )

    image_format = Format.DICONDE if revision == 0 else Format.JPEG
    purpose = Purpose.RECORD if revision == 0 else Purpose.VIEWING
    return StoredImage(
        radiograph_number=number,
        filename=expected_name(number, image_format, revision),
        image_format=image_format,
        bit_depth=16 if revision == 0 else 8,
        width=2048,
        height=512,
        byte_size=1024,
        digest=digest_of(f"{number}-{revision}".encode()),
        captured_at=datetime(2024, 11, 3, 22, 0),
        purpose=purpose,
    )


def test_the_report_sheet_carries_the_concession():
    body = rows(to_text(a_package(reports=(a_report(closed=True),)), "reports"))
    columns = sheet_named("reports").columns
    assert body[1][columns.index("concession")] == "CON-1"
    assert body[1][columns.index("closed_by")] == "0303"


def test_an_open_report_goes_out_with_the_closing_columns_empty():
    body = rows(to_text(a_package(reports=(a_report(),)), "reports"))
    columns = sheet_named("reports").columns
    assert body[1][columns.index("disposition")] == ""
    assert body[1][columns.index("closed_on")] == ""


def test_a_job_with_no_reports_still_writes_a_header():
    body = rows(to_text(a_package(), "reports"))
    assert len(body) == 1
    assert tuple(body[0]) == sheet_named("reports").columns


def test_the_file_sheet_carries_the_digest_the_client_checks_against():
    body = rows(to_text(a_package(files=(a_file(),)), "files"))
    assert len(body[1][sheet_named("files").columns.index("sha256")]) == 64


def test_the_file_sheet_says_which_copy_is_the_record():
    package = a_package(files=(a_file(), a_file(revision=1)))
    body = rows(to_text(package, "files"))
    purposes = {line[0]: line[sheet_named("files").columns.index("purpose")] for line in body[1:]}
    assert purposes["24-0417-R0001.dcm"] == "record"
    assert purposes["24-0417-R0001-v1.jpg"] == "viewing"


def test_the_manifest_is_a_sheet_like_the_rest_now():
    assert "files" in sheets()
    assert "reports" in sheets()
    assert len(to_files(a_package())) == len(sheets())
