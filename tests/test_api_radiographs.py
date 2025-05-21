import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

SHOT = "2024-10-23T23:30:00"
READ = "2024-10-24T08:00:00"
BASE = "/jobs/24-0001/welds/GW-104"


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


def a_shot(index=1, visible=True, density="2.50", **extra):
    body = {
        "exposure_index": index,
        "exposed_at": SHOT,
        "technique_reference": "RT-001 rev 1",
        "medium": "film",
        "film": "d7",
        "penetrated": {"text": "25.94 mm"},
        "indicator": {"hole_identity": 30, "hole_visible": visible},
        "densities": [
            {"label": "indicator", "density": density, "through_indicator": True},
            {"label": "weld", "density": density},
        ],
        "calibration": {
            "checked_at": "2024-10-23T18:00:00",
            "step_nominal": "2.00",
            "step_read": "2.01",
            "instrument": "X-Rite 301",
        },
    }
    body.update(extra)
    return body


def test_a_shot_is_recorded_and_numbered(api):
    response = api.post(f"{BASE}/shots", json=a_shot())
    assert response.status_code == 201
    body = response.json()
    assert body["number"] == "24-0001-R0001"
    assert body["admissible"]
    assert body["gates_run"] == ["indicator", "density"]


def test_an_invisible_hole_makes_the_shot_inadmissible(api):
    body = api.post(f"{BASE}/shots", json=a_shot(visible=False)).json()
    assert not body["admissible"]
    assert "not visible" in body["reasons"][0]


def test_a_thin_film_fails_on_density(api):
    body = api.post(f"{BASE}/shots", json=a_shot(density="1.40")).json()
    assert not body["admissible"]
    assert any("floor" in r for r in body["reasons"])


def test_the_same_exposure_twice_is_a_conflict(api):
    api.post(f"{BASE}/shots", json=a_shot(1))
    assert api.post(f"{BASE}/shots", json=a_shot(1)).status_code == 409


def test_an_exposure_index_below_one_is_refused_by_the_schema(api):
    assert api.post(f"{BASE}/shots", json=a_shot(0)).status_code == 422


def test_a_digital_shot_is_graded_on_noise_instead(api):
    body = api.post(
        f"{BASE}/shots",
        json=a_shot(
            medium="computed radiography",
            film="ip1_hd",
            indicator={"wire_number": 11, "finest_wire_visible": 12},
            densities=[],
            mean_grey="12000",
            noise="100",
            resolution={"text": "0.040 mm"},
        ),
    ).json()
    assert body["admissible"]
    assert body["gates_run"] == ["indicator", "normalised snr", "basic spatial resolution"]


def test_a_shot_with_no_indicator_at_all_is_refused(api):
    response = api.post(f"{BASE}/shots", json=a_shot(indicator={}))
    assert response.status_code == 422


def test_a_technique_class_nothing_knows_about_is_refused(api):
    response = api.post(f"{BASE}/shots", json=a_shot(technique_class="C"))
    assert response.status_code == 422


def test_a_reading_is_recorded_against_the_film(api):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    response = api.post(
        f"{BASE}/radiographs/{number}/reading",
        json={"reader_badge": "0142", "read_at": READ},
    )
    assert response.status_code == 200
    assert response.json()["read_by"] == "0142"


def test_indications_are_recorded_with_the_reading(api):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    response = api.post(
        f"{BASE}/radiographs/{number}/reading",
        json={
            "reader_badge": "0142",
            "read_at": READ,
            "indications": [
                {
                    "kind": "ESI",
                    "starts_at": {"text": "100 mm"},
                    "length": {"text": "20 mm"},
                    "width": {"text": "1 mm"},
                    "clock": "3:00",
                }
            ],
        },
    )
    assert response.status_code == 200


def test_a_discontinuity_code_nothing_knows_about_is_refused(api):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    response = api.post(
        f"{BASE}/radiographs/{number}/reading",
        json={
            "reader_badge": "0142",
            "read_at": READ,
            "indications": [
                {"kind": "ZZ", "starts_at": {"text": "100 mm"}, "length": {"text": "20 mm"}}
            ],
        },
    )
    assert response.status_code == 422


def test_a_film_cannot_be_read_twice(api):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    body = {"reader_badge": "0142", "read_at": READ}
    api.post(f"{BASE}/radiographs/{number}/reading", json=body)
    assert api.post(f"{BASE}/radiographs/{number}/reading", json=body).status_code == 409


def test_an_inadmissible_film_cannot_be_read(api):
    number = api.post(f"{BASE}/shots", json=a_shot(visible=False)).json()["number"]
    response = api.post(
        f"{BASE}/radiographs/{number}/reading",
        json={"reader_badge": "0142", "read_at": READ},
    )
    assert response.status_code == 409


def test_a_film_that_does_not_exist_is_a_four_oh_four(api):
    response = api.post(
        f"{BASE}/radiographs/24-0001-R9999/reading",
        json={"reader_badge": "0142", "read_at": READ},
    )
    assert response.status_code == 404


def test_the_openapi_document_lists_the_radiograph_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/jobs/{number}/welds/{weld}/shots" in paths


AGAIN = "2024-10-27T09:00:00"
SLAG = [
    {
        "kind": "ESI",
        "starts_at": {"text": "100 mm"},
        "length": {"text": "20 mm"},
        "width": {"text": "1 mm"},
    }
]
POROSITY = [
    {
        "kind": "P",
        "starts_at": {"text": "100 mm"},
        "length": {"text": "3 mm"},
        "width": {"text": "3 mm"},
    }
]


def read_film(api, indications=None):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    api.post(
        f"{BASE}/radiographs/{number}/reading",
        json={"reader_badge": "0142", "read_at": READ, "indications": indications or SLAG},
    )
    return number


def test_a_reread_names_both_readers(api):
    number = read_film(api)
    response = api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={
            "reader_badge": "0201",
            "read_at": AGAIN,
            "reason": "client asked",
            "indications": POROSITY,
        },
    )
    assert response.status_code == 200
    assert response.json()["readers"] == ["0142", "0201"]
    assert response.json()["changed"] is True


def test_a_reread_that_agrees_reports_no_change(api):
    number = read_film(api)
    response = api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={
            "reader_badge": "0201",
            "read_at": AGAIN,
            "reason": "second look",
            "indications": SLAG,
        },
    )
    assert response.json()["changed"] is False


def test_a_reread_with_no_reason_is_refused_by_the_schema(api):
    number = read_film(api)
    response = api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={"reader_badge": "0201", "read_at": AGAIN, "reason": ""},
    )
    assert response.status_code == 422


def test_a_reread_by_the_first_reader_is_a_conflict(api):
    number = read_film(api)
    response = api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={"reader_badge": "0142", "read_at": AGAIN, "reason": "why"},
    )
    assert response.status_code == 409


def test_a_reread_of_an_unread_film_is_a_conflict(api):
    number = api.post(f"{BASE}/shots", json=a_shot()).json()["number"]
    response = api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={"reader_badge": "0201", "read_at": AGAIN, "reason": "why"},
    )
    assert response.status_code == 409


def test_the_history_of_a_film_nobody_argued_about_is_empty(api):
    number = read_film(api)
    response = api.get(f"{BASE}/radiographs/{number}/history")
    assert response.status_code == 200
    assert response.json()["entries"] == []
    assert response.json()["after_signing"] == 0


def test_the_history_carries_what_the_first_reader_said(api):
    number = read_film(api)
    api.post(
        f"{BASE}/radiographs/{number}/reread",
        json={
            "reader_badge": "0201",
            "read_at": AGAIN,
            "reason": "client asked",
            "indications": POROSITY,
        },
    )
    body = api.get(f"{BASE}/radiographs/{number}/history").json()
    assert len(body["entries"]) == 1
    assert body["entries"][0]["action"] == "changed"
    assert body["entries"][0]["was"].startswith("ESI")
    assert body["entries"][0]["reason"] == "client asked"


def test_the_history_of_a_film_that_does_not_exist_is_a_404(api):
    response = api.get(f"{BASE}/radiographs/24-0001-R9999/history")
    assert response.status_code == 404
