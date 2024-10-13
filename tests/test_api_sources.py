import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine, unit_of_work_factory
from qu_imager.services.sources import register_device

ASSAY = "2024-10-01T00:00:00"


@pytest.fixture
def api():
    engine = build_engine()
    app = build_app_for(engine, create=True)
    from qu_imager.persistence.unit_of_work import transaction

    with transaction(unit_of_work_factory(engine)) as work:
        register_device(work, "880D-221", "880d")
    app.state.session_factory = unit_of_work_factory(engine)
    return TestClient(app)


def a_source(serial="IR-5512", isotope="ir192"):
    return {
        "serial": serial,
        "isotope": isotope,
        "assay_curies": "90",
        "assay_at": ASSAY,
        "manufacturer": "QSA",
        "model": "A424-9",
        "active_diameter": {"text": "3 mm"},
        "active_length": {"text": "3.5 mm"},
    }


def a_booking(site="Come By Chance"):
    return {
        "device_serial": "880D-221",
        "crew_badge": "0142",
        "site": site,
        "went_out": "2024-10-08",
    }


def test_a_source_is_registered_and_read_back(api):
    response = api.post("/sources", json=a_source())
    assert response.status_code == 201
    body = response.json()
    assert body["serial"] == "IR-5512"
    assert body["assay_activity"]["curie"] == "90"
    assert body["state"] == "in service"
    assert body["location"] == "shop"


def test_the_focal_size_is_the_larger_pellet_dimension(api):
    api.post("/sources", json=a_source())
    assert api.get("/sources/IR-5512").json()["focal_size"]["mm"] == "3.5"


def test_the_same_serial_twice_is_a_conflict(api):
    api.post("/sources", json=a_source())
    response = api.post("/sources", json=a_source())
    assert response.status_code == 409


def test_an_isotope_nothing_knows_about_is_refused(api):
    response = api.post("/sources", json=a_source(isotope="ra226"))
    assert response.status_code == 500
    assert response.json()["code"] == "qi.configuration"


def test_an_activity_of_nothing_is_refused_by_the_schema(api):
    body = a_source()
    body["assay_curies"] = "0"
    assert api.post("/sources", json=body).status_code == 422


def test_a_source_that_is_not_there_is_a_four_oh_four(api):
    assert api.get("/sources/IR-9999").status_code == 404


def test_the_sources_in_service_are_listed(api):
    api.post("/sources", json=a_source("IR-5512"))
    api.post("/sources", json=a_source("IR-6000"))
    assert [s["serial"] for s in api.get("/sources").json()] == ["IR-5512", "IR-6000"]


def test_a_source_booked_out_reads_as_the_site(api):
    api.post("/sources", json=a_source())
    response = api.post("/sources/IR-5512/send-out", json=a_booking())
    assert response.status_code == 200
    assert response.json()["location"] == "Come By Chance"


def test_a_source_cannot_go_out_twice(api):
    api.post("/sources", json=a_source())
    api.post("/sources/IR-5512/send-out", json=a_booking())
    assert api.post("/sources/IR-5512/send-out", json=a_booking("Elsewhere")).status_code == 409


def test_a_device_that_is_not_on_the_inventory_is_a_four_oh_four(api):
    api.post("/sources", json=a_source())
    booking = a_booking()
    booking["device_serial"] = "880D-999"
    assert api.post("/sources/IR-5512/send-out", json=booking).status_code == 404


def test_bringing_a_source_back_puts_it_in_the_shop(api):
    api.post("/sources", json=a_source())
    api.post("/sources/IR-5512/send-out", json=a_booking())
    response = api.post(
        "/sources/IR-5512/bring-back", json={"came_back": "2024-10-10", "exposures": 24}
    )
    assert response.status_code == 200
    assert response.json()["location"] == "shop"


def test_a_return_before_the_departure_is_refused(api):
    api.post("/sources", json=a_source())
    api.post("/sources/IR-5512/send-out", json=a_booking())
    response = api.post(
        "/sources/IR-5512/bring-back", json={"came_back": "2024-10-01", "exposures": 0}
    )
    assert response.status_code == 422


def test_fitness_is_answered_for_the_moment_it_is_asked_about(api):
    api.post("/sources", json=a_source())
    soon = api.get("/sources/IR-5512/fitness", params={"at": "2024-10-08T22:00:00"}).json()
    later = api.get("/sources/IR-5512/fitness", params={"at": "2026-10-08T22:00:00"}).json()
    assert soon["reasons"] == ["leak test is not current"]
    assert "activity is below the working floor" in later["reasons"]
    assert len(later["reasons"]) > len(soon["reasons"])


def test_an_unwiped_source_is_reported_unfit_with_the_reason(api):
    api.post("/sources", json=a_source())
    body = api.get("/sources/IR-5512/fitness", params={"at": "2024-10-08T22:00:00"}).json()
    assert not body["fit"]
    assert "leak test is not current" in body["reasons"]


def test_a_retired_source_says_so_and_stops_going_out(api):
    api.post("/sources", json=a_source())
    assert api.post("/sources/IR-5512/retire").json()["state"] == "awaiting disposal"
    assert api.post("/sources/IR-5512/send-out", json=a_booking()).status_code == 409


def test_a_source_out_on_a_job_cannot_be_retired(api):
    api.post("/sources", json=a_source())
    api.post("/sources/IR-5512/send-out", json=a_booking())
    assert api.post("/sources/IR-5512/retire").status_code == 409


def test_the_openapi_document_lists_the_source_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/sources/{serial}/fitness" in paths
    assert "/sources/{serial}/send-out" in paths
