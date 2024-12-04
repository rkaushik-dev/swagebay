import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

WHEN = "2024-12-05T23:30:00"
DUE = "2025-06-01T00:00:00"


@pytest.fixture
def api():
    return TestClient(build_app_for(build_engine(), create=True))


def a_field(collimator=None, material=None, thickness=None):
    body = {"isotope": "ir192", "curies": "74"}
    if collimator is not None:
        body["collimator"] = collimator
    if material is not None:
        body["barrier_material"] = material
    if thickness is not None:
        body["barrier_thickness"] = {"text": thickness}
    return body


def a_survey(stage="after", points=None):
    return {
        "stage": stage,
        "performed_at": WHEN,
        "performed_by": "0142",
        "instrument": "Ludlum 3",
        "calibration_due": DUE,
        "points": points
        if points is not None
        else [
            {"label": "boundary", "distance": {"text": "140 m"}, "mr_per_hour": "2"},
            {
                "label": "device",
                "distance": {"text": "1 m"},
                "mr_per_hour": "1",
                "at_device_surface": True,
            },
        ],
    }


def test_the_boundaries_come_back_for_every_posting(api):
    body = api.post("/safety/boundaries", json=a_field()).json()
    kinds = [b["kind"] for b in body["boundaries"]]
    assert kinds == ["restricted area", "radiation area", "high radiation area"]


def test_a_bare_source_is_not_directional(api):
    body = api.post("/safety/boundaries", json=a_field()).json()
    assert not body["directional"]
    first = body["boundaries"][0]
    assert first["down_beam"] == first["off_beam"]


def test_a_collimator_makes_it_directional(api):
    body = api.post("/safety/boundaries", json=a_field(collimator="w60")).json()
    assert body["directional"]
    first = body["boundaries"][0]
    assert int(first["off_beam"]["um"]) < int(first["down_beam"]["um"])


def test_a_barrier_pulls_every_boundary_in(api):
    bare = api.post("/safety/boundaries", json=a_field()).json()
    shielded = api.post(
        "/safety/boundaries", json=a_field(material="lead", thickness="30 mm")
    ).json()
    for left, right in zip(bare["boundaries"], shielded["boundaries"], strict=True):
        assert int(right["down_beam"]["um"]) < int(left["down_beam"]["um"])


def test_a_barrier_with_no_thickness_is_refused(api):
    response = api.post("/safety/boundaries", json=a_field(material="lead"))
    assert response.status_code == 422


def test_an_isotope_or_collimator_nothing_knows_about_is_refused(api):
    assert api.post("/safety/boundaries", json=a_field(collimator="w99")).status_code == 500
    body = a_field()
    body["isotope"] = "ra226"
    assert api.post("/safety/boundaries", json=body).status_code == 500


def test_a_source_of_nothing_is_refused_by_the_schema(api):
    body = a_field()
    body["curies"] = "0"
    assert api.post("/safety/boundaries", json=body).status_code == 422


def test_a_sound_survey_comes_back_clear(api):
    response = api.post("/safety/radiographs/R-001/surveys", json=a_survey())
    assert response.status_code == 201
    body = response.json()
    assert body["clear"]
    assert body["faults"] == []
    assert body["hottest_label"] == "boundary"


def test_a_survey_with_a_hot_device_is_stored_and_reported(api):
    points = [
        {
            "label": "device",
            "distance": {"text": "1 m"},
            "mr_per_hour": "900",
            "at_device_surface": True,
        }
    ]
    body = api.post("/safety/radiographs/R-001/surveys", json=a_survey(points=points)).json()
    assert not body["clear"]
    assert "not fully retracted" in body["faults"][0]


def test_an_after_survey_with_no_surface_reading_is_a_fault(api):
    points = [{"label": "boundary", "distance": {"text": "140 m"}, "mr_per_hour": "2"}]
    body = api.post("/safety/radiographs/R-001/surveys", json=a_survey(points=points)).json()
    assert "no reading was taken at the device surface" in body["faults"]


def test_a_before_survey_needs_no_surface_reading(api):
    points = [{"label": "boundary", "distance": {"text": "140 m"}, "mr_per_hour": "2"}]
    body = api.post(
        "/safety/radiographs/R-001/surveys", json=a_survey(stage="before", points=points)
    ).json()
    assert body["clear"]


def test_a_stage_nobody_publishes_is_refused_by_the_schema(api):
    assert (
        api.post("/safety/radiographs/R-001/surveys", json=a_survey(stage="during")).status_code
        == 422
    )


def test_a_survey_with_no_readings_is_refused(api):
    assert (
        api.post("/safety/radiographs/R-001/surveys", json=a_survey(points=[])).status_code == 422
    )


def test_the_openapi_document_lists_the_safety_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/safety/boundaries" in paths
    assert "/safety/radiographs/{radiograph}/surveys" in paths
