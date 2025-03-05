import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

SHOT = "2025-03-06T23:30:00"
BASE = "/jobs/25-0001"


def a_shot(index):
    return {
        "exposure_index": index,
        "exposed_at": SHOT,
        "technique_reference": "RT-001 rev 1",
        "medium": "film",
        "film": "d7",
        "penetrated": {"text": "25.94 mm"},
        "indicator": {"hole_identity": 30, "hole_visible": True},
        "densities": [
            {"label": "indicator", "density": "2.50", "through_indicator": True},
            {"label": "weld", "density": "2.50"},
        ],
        "calibration": {
            "checked_at": "2025-03-06T18:00:00",
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
            "client": "c",
            "site": "s",
            "acceptance_code": "API 1104",
            "procedure_number": "RT-001",
            "opened_on": "2025-03-01",
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
            "welded_on": "2025-03-01",
        },
    )
    for index in range(1, 13):
        client.post(f"{BASE}/welds/GW-104/shots", json=a_shot(index))
    return client


def test_a_short_listing_comes_back_as_one_last_page(api):
    body = api.get(f"{BASE}/radiographs", params={"size": 50}).json()
    assert len(body["items"]) == 12
    assert body["last"]
    assert body["next_cursor"] is None


def test_a_long_listing_is_cut_and_carries_a_cursor(api):
    body = api.get(f"{BASE}/radiographs", params={"size": 5}).json()
    assert len(body["items"]) == 5
    assert not body["last"]
    assert body["next_cursor"] == body["items"][-1]["number"]


def test_the_cursor_walks_the_whole_listing_without_repeating(api):
    seen = []
    cursor = None
    while True:
        params = {"size": 5}
        if cursor is not None:
            params["cursor"] = cursor
        body = api.get(f"{BASE}/radiographs", params=params).json()
        seen.extend(item["number"] for item in body["items"])
        if body["last"]:
            break
        cursor = body["next_cursor"]
    assert len(seen) == 12
    assert len(set(seen)) == 12
    assert seen == sorted(seen)


def test_a_blank_cursor_starts_at_the_beginning(api):
    first = api.get(f"{BASE}/radiographs", params={"size": 3}).json()
    blank = api.get(f"{BASE}/radiographs", params={"size": 3, "cursor": "  "}).json()
    assert first["items"] == blank["items"]


def test_a_page_size_below_one_is_refused(api):
    assert api.get(f"{BASE}/radiographs", params={"size": 0}).status_code == 422


def test_a_page_size_over_the_ceiling_is_clamped_rather_than_refused(api):
    body = api.get(f"{BASE}/radiographs", params={"size": 100000}).json()
    assert body["last"]
    assert len(body["items"]) == 12


def test_the_listing_carries_the_weld_each_film_belongs_to(api):
    body = api.get(f"{BASE}/radiographs", params={"size": 2}).json()
    assert all(item["weld"] == "GW-104" for item in body["items"])
    assert body["items"][0]["admissible"]


def test_a_job_that_is_not_there_is_a_four_oh_four(api):
    assert api.get("/jobs/25-0099/radiographs").status_code == 404


def test_the_openapi_document_lists_the_paged_route(api):
    assert "/jobs/{number}/radiographs" in api.get("/openapi.json").json()["paths"]
