import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

OPENED = "2024-09-30"


@pytest.fixture
def api():
    return TestClient(build_app_for(build_engine(), create=True))


def a_job(client="Maritime Pipelines", code="API 1104"):
    return {
        "client": client,
        "site": "Come By Chance",
        "acceptance_code": code,
        "procedure_number": "RT-001",
        "opened_on": OPENED,
    }


def a_weld(weld="GW-104"):
    return {
        "weld": weld,
        "joint": {
            "material": "carbon_steel",
            "wall": {"text": "10.97 mm"},
            "outside_diameter": {"text": "168.3 mm"},
            "reinforcement": {"text": "2 mm"},
        },
        "weld_length": {"text": "529 mm"},
        "welder": "w-19",
        "welded_on": OPENED,
    }


def test_a_job_is_created_and_numbered(api):
    response = api.post("/jobs", json=a_job())
    assert response.status_code == 201
    body = response.json()
    assert body["number"] == "24-0001"
    assert body["welds"] == 0
    assert body["closed_on"] is None


def test_the_numbers_run_on(api):
    first = api.post("/jobs", json=a_job()).json()
    second = api.post("/jobs", json=a_job("Other")).json()
    assert first["number"] == "24-0001"
    assert second["number"] == "24-0002"


def test_an_acceptance_code_nothing_implements_is_refused(api):
    response = api.post("/jobs", json=a_job(code="BS 2600"))
    assert response.status_code == 422
    assert response.json()["code"] == "qi.validation"
    assert response.json()["context"]["given"] == "BS 2600"


def test_a_job_is_read_back_by_its_number(api):
    api.post("/jobs", json=a_job())
    response = api.get("/jobs/24-0001")
    assert response.status_code == 200
    assert response.json()["client"] == "Maritime Pipelines"


def test_a_job_that_is_not_there_is_a_four_oh_four(api):
    response = api.get("/jobs/24-0099")
    assert response.status_code == 404
    assert response.json()["code"] == "qi.not_found"


def test_a_malformed_job_number_is_refused_with_the_grammar(api):
    response = api.get("/jobs/nonsense")
    assert response.status_code == 422
    assert response.json()["code"] == "qi.validation"
    assert response.json()["context"]["text"] == "nonsense"


def test_the_open_jobs_are_listed_for_a_day(api):
    api.post("/jobs", json=a_job())
    response = api.get("/jobs", params={"on": "2024-10-01"})
    assert [j["number"] for j in response.json()] == ["24-0001"]
    assert api.get("/jobs", params={"on": "2024-01-01"}).json() == []


def test_a_weld_is_added_to_a_job(api):
    api.post("/jobs", json=a_job())
    response = api.post("/jobs/24-0001/welds", json=a_weld())
    assert response.status_code == 201
    body = response.json()
    assert body["weld"] == "GW-104"
    assert body["wall"]["mm"] == "10.97"
    assert body["outside_diameter"]["mm"] == "168.3"
    assert body["radiographs"] == 0


def test_a_plate_weld_comes_back_with_no_diameter(api):
    api.post("/jobs", json=a_job())
    plate = a_weld("GW-200")
    plate["joint"]["outside_diameter"] = None
    response = api.post("/jobs/24-0001/welds", json=plate)
    assert response.status_code == 201
    assert response.json()["outside_diameter"] is None


def test_the_same_weld_twice_is_a_conflict(api):
    api.post("/jobs", json=a_job())
    api.post("/jobs/24-0001/welds", json=a_weld())
    response = api.post("/jobs/24-0001/welds", json=a_weld())
    assert response.status_code == 409
    assert response.json()["code"] == "qi.conflict"


def test_a_wall_that_will_not_parse_is_refused_by_the_schema(api):
    api.post("/jobs", json=a_job())
    broken = a_weld()
    broken["joint"]["wall"] = {"text": "ten mm"}
    assert api.post("/jobs/24-0001/welds", json=broken).status_code == 422


def test_the_welds_on_a_job_are_listed_in_order(api):
    api.post("/jobs", json=a_job())
    for weld in ("GW-110", "GW-104", "GW-104R1"):
        api.post("/jobs/24-0001/welds", json=a_weld(weld))
    listed = [w["weld"] for w in api.get("/jobs/24-0001/welds").json()]
    assert listed == ["GW-104", "GW-104R1", "GW-110"]


def test_the_outstanding_list_says_what_is_missing(api):
    api.post("/jobs", json=a_job())
    body = api.get("/jobs/24-0001/outstanding").json()
    assert body["job"] == "24-0001"
    assert "the job has no welds on it" in body["items"]


def test_a_job_with_nothing_done_refuses_to_close(api):
    api.post("/jobs", json=a_job())
    response = api.post("/jobs/24-0001/close", params={"on": "2024-10-05"})
    assert response.status_code == 409
    assert response.json()["context"]["outstanding"] == 1


def test_the_job_count_of_welds_follows_what_was_added(api):
    api.post("/jobs", json=a_job())
    api.post("/jobs/24-0001/welds", json=a_weld())
    assert api.get("/jobs/24-0001").json()["welds"] == 1


def test_the_openapi_document_lists_the_job_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/jobs" in paths
    assert "/jobs/{number}/welds" in paths
    assert "post" in paths["/jobs"]
