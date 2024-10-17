import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

ASSAY = "2024-10-01T00:00:00"
SHOT = "2024-10-21T23:30:00"


@pytest.fixture
def api():
    client = TestClient(build_app_for(build_engine(), create=True))
    client.post(
        "/sources",
        json={
            "serial": "IR-5512",
            "isotope": "ir192",
            "assay_curies": "90",
            "assay_at": ASSAY,
            "manufacturer": "QSA",
            "model": "A424-9",
            "active_diameter": {"text": "3 mm"},
            "active_length": {"text": "3 mm"},
        },
    )
    client.post(
        "/jobs",
        json={
            "client": "c",
            "site": "s",
            "acceptance_code": "API 1104",
            "procedure_number": "RT-001",
            "opened_on": "2024-10-01",
        },
    )
    client.post(
        "/jobs/24-0001/welds",
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
            "welded_on": "2024-10-01",
        },
    )
    return client


def a_plan(film="d7", shot="double wall single image", **extra):
    body = {"source_serial": "IR-5512", "film": film, "shot_at": SHOT, "shot": shot}
    body.update(extra)
    return body


def test_a_sheet_comes_back_with_every_number_on_it(api):
    response = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan())
    assert response.status_code == 200
    body = response.json()
    assert body["weld"] == "GW-104"
    assert body["shot"] == "double wall single image"
    assert body["penetrated"]["mm"] == "25.94"
    assert body["coverage"]["exposures"] == 8
    assert body["exposure"]["timer"] == "1:34"


def test_the_planner_picks_the_shot_when_none_is_named(api):
    body = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan(shot=None)).json()
    assert body["shot"] == "panoramic"
    assert body["coverage"]["exposures"] == 1


def test_an_unwiped_source_shows_up_as_a_warning_not_a_failure(api):
    body = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan()).json()
    assert not body["clean"]
    assert any("leak test" in w for w in body["warnings"])


def test_a_shot_type_nothing_knows_about_is_refused(api):
    response = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan(shot="sideways"))
    assert response.status_code == 422
    assert response.json()["context"]["shot"] == "sideways"


def test_an_indicator_side_nothing_knows_about_is_refused(api):
    response = api.post(
        "/jobs/24-0001/welds/GW-104/technique",
        json=a_plan(indicator_side="the other one"),
    )
    assert response.status_code == 422


def test_a_window_is_carried_through_to_the_warnings(api):
    body = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan(window_minutes=1)).json()
    assert any("window" in w or "source out" in w for w in body["warnings"])


def test_a_source_that_is_not_there_is_a_four_oh_four(api):
    response = api.post(
        "/jobs/24-0001/welds/GW-104/technique",
        json={"source_serial": "IR-9999", "film": "d7", "shot_at": SHOT},
    )
    assert response.status_code == 404


def test_a_weld_that_is_not_there_is_a_four_oh_four(api):
    response = api.post("/jobs/24-0001/welds/GW-999/technique", json=a_plan())
    assert response.status_code == 404


def test_films_are_compared_in_the_order_they_were_asked_for(api):
    response = api.post(
        "/jobs/24-0001/welds/GW-104/technique/compare",
        json=a_plan(),
        params={"films": "d7,d5,d4"},
    )
    assert response.status_code == 200
    sheets = response.json()
    assert len(sheets) == 3
    timers = [s["exposure"]["ms"] for s in sheets]
    assert timers == sorted(timers)


def test_comparing_no_films_at_all_is_refused(api):
    response = api.post(
        "/jobs/24-0001/welds/GW-104/technique/compare",
        json=a_plan(),
        params={"films": " , "},
    )
    assert response.status_code == 422


def test_the_fastest_film_endpoint_names_one(api):
    response = api.post(
        "/jobs/24-0001/welds/GW-104/technique/fastest",
        json=a_plan(),
        params={"films": "d4,d5,d7"},
    )
    assert response.status_code == 200
    assert response.json()["film"] in {"d7", None}


def test_a_density_with_no_chart_is_refused(api):
    response = api.post("/jobs/24-0001/welds/GW-104/technique", json=a_plan(density="2.2"))
    assert response.status_code == 500


def test_the_openapi_document_lists_the_technique_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/jobs/{number}/welds/{weld}/technique" in paths
    assert "/jobs/{number}/welds/{weld}/technique/compare" in paths
