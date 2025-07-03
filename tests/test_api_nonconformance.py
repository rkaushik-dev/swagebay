import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine

RAISED = "2025-07-02"
LATER = "2025-07-08"
MUCH_LATER = "2025-09-01"


@pytest.fixture
def api():
    return TestClient(build_app_for(build_engine(), create=True))


def a_report(reference="NCR-1", job="24-0001", by="0142"):
    return {
        "reference": reference,
        "job": job,
        "item": "GW-104",
        "requirement": "API 1104 9.3.8",
        "account": "elongated slag 32 mm in a 300 mm length",
        "raised_on": RAISED,
        "raised_by": by,
    }


def test_a_report_is_raised_and_read_back(api):
    response = api.post("/nonconformances", json=a_report())
    assert response.status_code == 201
    body = response.json()
    assert body["disposition"] is None
    assert body["closed_on"] is None
    assert body["days_open"] == 0


def test_the_same_reference_twice_is_a_409(api):
    api.post("/nonconformances", json=a_report())
    assert api.post("/nonconformances", json=a_report()).status_code == 409


def test_a_report_with_no_account_is_a_422(api):
    body = a_report()
    body["account"] = ""
    assert api.post("/nonconformances", json=body).status_code == 422


def test_a_reference_nobody_raised_is_a_404(api):
    assert api.get("/nonconformances/NCR-9").status_code == 404


def test_a_decision_comes_back_on_the_report(api):
    api.post("/nonconformances", json=a_report())
    body = api.post("/nonconformances/NCR-1/decision", json={"disposition": "rework"}).json()
    assert body["disposition"] == "rework"


def test_use_as_is_without_a_concession_is_a_409(api):
    api.post("/nonconformances", json=a_report())
    response = api.post("/nonconformances/NCR-1/decision", json={"disposition": "use_as_is"})
    assert response.status_code == 409


def test_use_as_is_with_a_concession_is_accepted(api):
    api.post("/nonconformances", json=a_report())
    body = api.post(
        "/nonconformances/NCR-1/decision",
        json={"disposition": "use_as_is", "concession": "CON-88"},
    ).json()
    assert body["concession"] == "CON-88"


def test_a_disposition_nothing_knows_about_is_a_422(api):
    api.post("/nonconformances", json=a_report())
    response = api.post("/nonconformances/NCR-1/decision", json={"disposition": "ponder"})
    assert response.status_code == 422
    assert "rework" in response.json()["context"]["known"]


def test_closing_without_a_decision_is_a_409(api):
    api.post("/nonconformances", json=a_report())
    response = api.post(
        "/nonconformances/NCR-1/closure", json={"closed_on": LATER, "closed_by": "0303"}
    )
    assert response.status_code == 409


def test_the_author_closing_it_is_a_409(api):
    api.post("/nonconformances", json=a_report())
    api.post("/nonconformances/NCR-1/decision", json={"disposition": "rework"})
    response = api.post(
        "/nonconformances/NCR-1/closure", json={"closed_on": LATER, "closed_by": "0142"}
    )
    assert response.status_code == 409


def test_somebody_else_closing_it_works(api):
    api.post("/nonconformances", json=a_report())
    api.post("/nonconformances/NCR-1/decision", json={"disposition": "rework"})
    body = api.post(
        "/nonconformances/NCR-1/closure", json={"closed_on": LATER, "closed_by": "0303"}
    ).json()
    assert body["closed_by"] == "0303"
    assert body["days_open"] == 6


def test_the_open_list_spans_jobs_and_drops_the_closed_ones(api):
    api.post("/nonconformances", json=a_report("NCR-1"))
    api.post("/nonconformances", json=a_report("NCR-2", job="24-0002"))
    api.post("/nonconformances/NCR-1/decision", json={"disposition": "rework"})
    api.post("/nonconformances/NCR-1/closure", json={"closed_on": LATER, "closed_by": "0303"})
    body = api.get("/nonconformances").json()
    assert [r["reference"] for r in body] == ["NCR-2"]


def test_overdue_is_answered_for_the_day_it_is_asked_about(api):
    api.post("/nonconformances", json=a_report())
    soon = api.get("/nonconformances", params={"on": LATER}).json()[0]
    later = api.get("/nonconformances", params={"on": MUCH_LATER}).json()[0]
    assert not soon["overdue"]
    assert later["overdue"]


def test_the_openapi_document_lists_the_report_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/nonconformances/{reference}/decision" in paths
    assert "/nonconformances/{reference}/closure" in paths
