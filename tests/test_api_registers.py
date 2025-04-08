import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

FOUND = "2025-04-09T23:30:00"


@pytest.fixture
def api():
    return TestClient(build_app_for(build_engine(), create=True))


def an_incident(reference="INC-1", kind="OVEREXPOSURE"):
    return {
        "reference": reference,
        "occurrence": kind,
        "discovered_at": FOUND,
        "site": "Come By Chance",
        "reported_by": "0142",
        "account": "the source hung up",
    }


def a_calibration(serial="LU-1", due="2026-04-09"):
    return {
        "serial": serial,
        "instrument": "SURVEY_METER",
        "calibrated_on": "2025-04-09",
        "due_on": due,
        "laboratory": "NRC",
        "certificate": "C-1",
    }


def test_an_incident_comes_back_with_the_deadline_it_computed(api):
    response = api.post("/registers/incidents", json=an_incident())
    assert response.status_code == 201
    body = response.json()
    assert body["reference"] == "INC-1"
    assert body["due_at"] == "2025-04-10T23:30:00"
    assert body["notified_at"] is None


def test_a_stuck_source_gets_the_hour_deadline(api):
    body = api.post("/registers/incidents", json=an_incident("INC-2", "SOURCE_DISCONNECTED")).json()
    assert body["due_at"] == "2025-04-10T00:30:00"


def test_the_occurrence_may_be_named_by_its_words_too(api):
    body = api.post("/registers/incidents", json=an_incident("INC-3", "dose over a limit")).json()
    assert body["due_at"] == "2025-04-10T23:30:00"


def test_an_occurrence_nobody_publishes_is_refused(api):
    response = api.post("/registers/incidents", json=an_incident("INC-4", "a bad day"))
    assert response.status_code == 422
    assert "SOURCE_DISCONNECTED" in response.json()["context"]["known"]


def test_the_same_reference_twice_is_a_conflict(api):
    api.post("/registers/incidents", json=an_incident())
    assert api.post("/registers/incidents", json=an_incident()).status_code == 409


def test_the_unreported_list_empties_when_one_is_notified(api):
    api.post("/registers/incidents", json=an_incident())
    assert len(api.get("/registers/incidents").json()) == 1
    api.post("/registers/incidents/INC-1/notify", params={"at": FOUND})
    assert api.get("/registers/incidents").json() == []


def test_reporting_late_is_reported_as_late(api):
    api.post("/registers/incidents", json=an_incident())
    body = api.post(
        "/registers/incidents/INC-1/notify", params={"at": "2025-04-12T00:00:00"}
    ).json()
    assert body["late"]
    assert body["notified_at"] == "2025-04-12T00:00:00"


def test_reporting_in_time_is_not_late(api):
    api.post("/registers/incidents", json=an_incident())
    body = api.post(
        "/registers/incidents/INC-1/notify", params={"at": "2025-04-10T09:00:00"}
    ).json()
    assert not body["late"]


def test_an_incident_that_is_not_there_is_a_four_oh_four(api):
    assert api.get("/registers/incidents/INC-9").status_code == 404
    assert api.post("/registers/incidents/INC-9/notify", params={"at": FOUND}).status_code == 404


def test_a_calibration_is_recorded_and_read_back(api):
    response = api.post("/registers/calibrations", json=a_calibration())
    assert response.status_code == 201
    assert response.json()["due_on"] == "2026-04-09"
    assert api.get("/registers/calibrations/LU-1").json()["laboratory"] == "NRC"


def test_an_instrument_never_calibrated_comes_back_empty(api):
    assert api.get("/registers/calibrations/LU-9").json() is None


def test_an_instrument_nobody_publishes_is_refused(api):
    body = a_calibration()
    body["instrument"] = "divining rod"
    assert api.post("/registers/calibrations", json=body).status_code == 422


def test_a_certificate_that_expires_before_it_was_issued_is_refused(api):
    assert api.post(
        "/registers/calibrations", json=a_calibration(due="2025-04-01")
    ).status_code in {409, 422, 500}


def test_the_expired_list_answers_for_a_day(api):
    api.post("/registers/calibrations", json=a_calibration())
    assert api.get("/registers/calibrations/expired", params={"on": "2025-05-01"}).json() == []
    assert api.get("/registers/calibrations/expired", params={"on": "2026-05-01"}).json() == [
        "LU-1"
    ]


def test_the_openapi_document_lists_the_register_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/registers/incidents" in paths
    assert "/registers/calibrations" in paths
