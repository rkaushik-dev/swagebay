import csv
import io

import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

SHOT = "2024-12-09T23:30:00"
READ = "2024-12-10T08:00:00"
BASE = "/jobs/24-0001"


def a_shot(index=1, visible=True):
    return {
        "exposure_index": index,
        "exposed_at": SHOT,
        "technique_reference": "RT-001 rev 1",
        "medium": "film",
        "film": "d7",
        "penetrated": {"text": "25.94 mm"},
        "indicator": {"hole_identity": 30, "hole_visible": visible},
        "densities": [
            {"label": "indicator", "density": "2.50", "through_indicator": True},
            {"label": "weld", "density": "2.50"},
        ],
        "calibration": {
            "checked_at": "2024-12-09T18:00:00",
            "step_nominal": "2.00",
            "step_read": "2.01",
            "instrument": "X-Rite 301",
        },
    }


@pytest.fixture
def api():
    client = TestClient(build_app_for(build_engine(), create=True))
    client.post(
        "/jobs",
        json={
            "client": "Maritime Pipelines",
            "site": "Come By Chance",
            "acceptance_code": "API 1104",
            "procedure_number": "RT-001",
            "opened_on": "2024-12-01",
        },
    )
    client.post(
        f"{BASE}/welds",
        json={
            "weld": "GW-104",
            "joint": {
                "material": "carbon_steel",
                "wall": {"text": "10.97 mm"},
                "outside_diameter": {"text": "168.3 mm"},
                "reinforcement": {"text": "2 mm"},
            },
            "weld_length": {"text": "529 mm"},
            "welder": "w-19",
            "welded_on": "2024-12-01",
        },
    )
    return client


def shoot_and_read(api, index=1, visible=True):
    number = api.post(f"{BASE}/welds/GW-104/shots", json=a_shot(index, visible)).json()["number"]
    if visible:
        api.post(
            f"{BASE}/welds/GW-104/radiographs/{number}/reading",
            json={"reader_badge": "0142", "read_at": READ},
        )
    return number


def test_an_unfinished_job_reports_its_faults(api):
    body = api.get(f"{BASE}/report").json()
    assert not body["complete"]
    assert "GW-104 has no radiographs" in body["faults"]


def test_a_finished_job_reports_complete_with_an_empty_fault_list(api):
    shoot_and_read(api)
    body = api.get(f"{BASE}/report").json()
    assert body["complete"]
    assert body["faults"] == []


def test_the_package_carries_the_job_details(api):
    body = api.get(f"{BASE}/report").json()
    assert body["job"] == "24-0001"
    assert body["client"] == "Maritime Pipelines"
    assert body["code"] == "API 1104"
    assert body["closed_on"] is None


def test_the_weld_summary_carries_the_disposition(api):
    shoot_and_read(api)
    body = api.get(f"{BASE}/report").json()
    assert body["welds"][0]["weld"] == "GW-104"
    assert body["welds"][0]["disposition"] == "accept"
    assert body["welds"][0]["wall_mm"] == "10.97"


def test_the_csv_comes_back_as_text_rather_than_json(api):
    shoot_and_read(api)
    response = api.get(f"{BASE}/report/welds.csv")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    rows = list(csv.reader(io.StringIO(response.text)))
    assert rows[0][0] == "weld"
    assert rows[1][0] == "GW-104"


def test_every_published_sheet_can_be_fetched(api):
    shoot_and_read(api)
    for sheet in api.get(f"{BASE}/report/sheets").json():
        assert api.get(f"{BASE}/report/{sheet}.csv").status_code == 200


def test_a_sheet_nobody_publishes_is_refused(api):
    assert api.get(f"{BASE}/report/everything.csv").status_code == 422


def test_the_summary_csv_is_one_row_under_its_header(api):
    shoot_and_read(api)
    rows = list(csv.reader(io.StringIO(api.get(f"{BASE}/report/summary.csv").text)))
    assert len(rows) == 2
    assert rows[1][0] == "24-0001"


def test_a_sound_job_has_no_broken_traceability_chains(api):
    shoot_and_read(api)
    assert api.get(f"{BASE}/report/traceability").json() == {}


def test_an_unread_film_shows_up_as_a_broken_chain(api):
    number = api.post(f"{BASE}/welds/GW-104/shots", json=a_shot()).json()["number"]
    broken = api.get(f"{BASE}/report/traceability").json()
    assert number in broken
    assert any("read by" in line for line in broken[number])


def test_a_job_that_is_not_there_is_a_four_oh_four(api):
    assert api.get("/jobs/24-0099/report").status_code == 404


def test_the_repair_rate_comes_back_on_the_package(api):
    shoot_and_read(api)
    assert api.get(f"{BASE}/report").json()["repair_rate"] == "0"


def test_the_openapi_document_lists_the_report_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/jobs/{number}/report" in paths
    assert "/jobs/{number}/report/{sheet}.csv" in paths
