from datetime import datetime
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from qu_imager.api.app import build_app_for
from qu_imager.persistence.unit_of_work import build_engine, transaction, unit_of_work_factory
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.safety.maintenance import pre_use, quarterly
from qu_imager.services.sources import register_device, register_source
from qu_imager.services.wipes import record_wipe
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import SealedSource, SourceCertificate

ASSAY = datetime(2025, 7, 1)
DAY = "2025-08-20"
STRIPPED = "2025-08-01"


@pytest.fixture
def api():
    engine = build_engine()
    app = build_app_for(engine, create=True)
    made = unit_of_work_factory(engine)
    app.state.session_factory = made
    client = TestClient(app)
    client.made = made
    return client


def stock(api, wiped=True, inspected=True):
    from datetime import date

    with transaction(api.made) as work:
        register_device(work, "D-77", "880d")
        register_source(
            work,
            SealedSource(
                certificate=SourceCertificate(
                    serial="IR-5512",
                    isotope=IR192,
                    assay_activity=curie(90),
                    assay_at=ASSAY,
                    manufacturer="QSA",
                    model="A424-9",
                    active_diameter=mm(3),
                    active_length=mm(3),
                ),
                working_floor=curie(15),
            ),
        )
        if wiped:
            record_wipe(work, "IR-5512", datetime(2025, 8, 1), Decimal(0), "rp")
        if inspected:
            work.maintenance.record(quarterly("D-77", date(2025, 8, 1), "0303"))
            work.maintenance.record(pre_use("D-77", date(2025, 8, 20), "0142"))


def test_a_quiet_morning_reads_as_quiet(api):
    stock(api)
    body = api.get("/morning", params={"on": DAY}).json()
    assert body["quiet"]
    assert body["stops_today"] == []


def test_a_device_with_nothing_on_record_stops_the_day(api):
    stock(api, inspected=False)
    body = api.get("/morning", params={"on": DAY}).json()
    assert not body["quiet"]
    assert any("no quarterly" in line for line in body["stops_today"])


def test_the_loads_are_only_listed_when_they_are_asked_for(api):
    stock(api)
    assert api.get("/morning", params={"on": DAY}).json()["loads_ready"] == []
    body = api.get("/morning", params={"on": DAY, "loads": True}).json()
    assert body["loads_ready"] == ["D-77 with IR-5512"]


def test_the_morning_defaults_to_today_when_no_day_is_given(api):
    stock(api)
    assert api.get("/morning").status_code == 200


def test_a_checklist_for_a_job_that_is_not_there_is_a_404(api):
    assert api.get("/jobs/25-0001/checklist").status_code == 404


def test_a_checklist_keeps_the_three_kinds_apart(api):
    api.post(
        "/jobs",
        json={
            "client": "c",
            "site": "s",
            "acceptance_code": "API 1104",
            "procedure_number": "RT-001",
            "opened_on": "2025-08-01",
        },
    )
    body = api.get("/jobs/25-0001/checklist").json()
    assert not body["clear"]
    assert body["work_reasons"] == ["the job has no welds on it"]
    assert body["report_reasons"] == []
    assert body["image_reasons"] == []


def test_an_open_report_shows_up_under_reports(api):
    api.post(
        "/jobs",
        json={
            "client": "c",
            "site": "s",
            "acceptance_code": "API 1104",
            "procedure_number": "RT-001",
            "opened_on": "2025-08-01",
        },
    )
    api.post(
        "/nonconformances",
        json={
            "reference": "NCR-1",
            "job": "25-0001",
            "item": "GW-104",
            "requirement": "API 1104 9.3.8",
            "account": "slag",
            "raised_on": "2025-08-02",
            "raised_by": "0142",
        },
    )
    body = api.get("/jobs/25-0001/checklist").json()
    assert body["report_reasons"] == ["NCR-1 is still open"]


def test_a_clean_shop_reads_as_healthy(api):
    stock(api)
    body = api.get("/doctor").json()
    assert body["healthy"]
    assert body["findings"] == []
    assert len(body["checks_run"]) == len(body["checks_available"])


def test_a_finding_comes_back_with_the_check_that_found_it(api):
    from datetime import date

    with transaction(api.made) as work:
        work.maintenance.record(quarterly("D-99", date(2025, 8, 1), "0303"))
    body = api.get("/doctor").json()
    assert not body["healthy"]
    assert body["findings"][0]["check"] == "unknown device"
    assert body["findings"][0]["subject"] == "D-99"


def test_only_the_named_checks_run(api):
    from datetime import date

    with transaction(api.made) as work:
        work.maintenance.record(quarterly("D-99", date(2025, 8, 1), "0303"))
    body = api.get("/doctor", params={"check": ["self closed"]}).json()
    assert body["checks_run"] == ["self closed"]
    assert body["healthy"]


def test_the_openapi_document_lists_the_operational_routes(api):
    paths = api.get("/openapi.json").json()["paths"]
    assert "/morning" in paths
    assert "/doctor" in paths
    assert "/jobs/{number}/checklist" in paths


def test_the_two_sheets_both_survive_in_the_document(api):
    schemas = api.get("/openapi.json").json()["components"]["schemas"]
    assert "MorningOut" in schemas
    assert "SheetOut" in schemas
