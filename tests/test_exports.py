import csv
import io
from datetime import date

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.identity import JobNumber
from qu_imager.quantities.length import mm
from qu_imager.reporting.exports import (
    RADIOGRAPH_COLUMNS,
    SUMMARY_COLUMNS,
    WELD_COLUMNS,
    columns_of,
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
    assert "wall_mm" in WELD_COLUMNS
    body = rows(to_text(a_package(), "welds"))
    assert body[1][WELD_COLUMNS.index("wall_mm")] == "10.97"


def test_the_weld_sheet_carries_the_disposition_and_the_clauses():
    body = rows(to_text(a_package(), "welds"))
    assert body[1][WELD_COLUMNS.index("disposition")] == "accept"
    assert body[2][WELD_COLUMNS.index("clauses")] == "9.3.8"


def test_a_weld_with_no_clauses_leaves_the_cell_empty():
    body = rows(to_text(a_package(), "welds"))
    assert body[1][WELD_COLUMNS.index("clauses")] == ""


def test_the_radiograph_sheet_writes_yes_and_no_rather_than_true_and_false():
    body = rows(to_text(a_package(), "radiographs"))
    assert body[1][RADIOGRAPH_COLUMNS.index("admissible")] == "yes"


def test_an_unread_film_leaves_the_reader_cell_empty():
    unread = (
        RadiographLine("24-0417-R0002", "GW-104", 2, OPENED, "RT-001 rev 1", "film", True, None, 0),
    )
    body = rows(to_text(a_package(radiographs=unread), "radiographs"))
    assert body[1][RADIOGRAPH_COLUMNS.index("read_by")] == ""


def test_the_summary_is_one_row_under_its_header():
    body = rows(to_text(a_package(), "summary"))
    assert len(body) == 2
    assert body[1][SUMMARY_COLUMNS.index("job")] == "24-0417"
    assert body[1][SUMMARY_COLUMNS.index("complete")] == "yes"


def test_an_incomplete_package_says_so_in_the_summary():
    body = rows(to_text(a_package(complete=False), "summary"))
    assert body[1][SUMMARY_COLUMNS.index("complete")] == "no"


def test_the_repair_rate_is_written_to_four_places():
    body = rows(to_text(a_package(), "summary"))
    assert body[1][SUMMARY_COLUMNS.index("repair_rate")] == "0.5000"


def test_an_open_job_leaves_the_close_date_empty():
    body = rows(to_text(a_package(), "summary"))
    assert body[1][SUMMARY_COLUMNS.index("closed_on")] == ""


def test_a_field_with_a_comma_in_it_is_quoted_properly():
    awkward = (WeldLine("GW-104", mm(10), "w-19, second shift", 1, "accept", ("9.3.1", "9.3.8")),)
    text = to_text(a_package(welds=awkward), "welds")
    assert '"w-19, second shift"' in text
    assert rows(text)[1][WELD_COLUMNS.index("welder")] == "w-19, second shift"


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
