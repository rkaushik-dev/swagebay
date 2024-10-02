import pytest
from fastapi import APIRouter
from fastapi.testclient import TestClient

from qu_imager import __version__
from qu_imager.api.app import Work, build_app_for
from qu_imager.api.errors import STATUS_BY_ERROR, error_body, status_for
from qu_imager.errors import (
    ConfigurationError,
    ConflictError,
    NotFoundError,
    QuImagerError,
    TechniqueError,
    ValidationError,
)
from qu_imager.identity import JobNumber
from qu_imager.persistence.models import JobRow
from qu_imager.persistence.unit_of_work import build_engine


def app_with(router=None):
    app = build_app_for(build_engine(), create=True)
    if router is not None:
        app.include_router(router)
    return app


def client(router=None):
    return TestClient(app_with(router))


def test_the_health_endpoint_answers_with_the_version():
    response = client().get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "version": __version__}


def test_each_error_maps_to_the_status_it_deserves():
    assert status_for(NotFoundError("source", "IR-1")) == 404
    assert status_for(ConflictError("busy")) == 409
    assert status_for(ValidationError("bad")) == 422
    assert status_for(TechniqueError("impossible")) == 422
    assert status_for(ConfigurationError("ours")) == 500
    assert status_for(QuImagerError("unclassified")) == 500


def test_the_more_specific_mapping_wins():
    kinds = [kind for kind, _status in STATUS_BY_ERROR]
    assert kinds.index(NotFoundError) < kinds.index(ValidationError)
    assert status_for(NotFoundError("weld", "GW-1")) == 404


def test_the_body_is_the_error_dictionary_unchanged():
    error = TechniqueError("too close", sfd_mm=400, minimum_mm=610)
    assert error_body(error) == error.as_dict()
    assert error_body(error)["code"] == "qi.technique"


def test_a_domain_error_from_a_handler_comes_back_as_its_status():
    router = APIRouter()

    @router.get("/boom")
    def boom() -> dict[str, str]:
        raise NotFoundError("source", "IR-9999")

    response = client(router).get("/boom")
    assert response.status_code == 404
    assert response.json()["code"] == "qi.not_found"
    assert response.json()["context"]["identity"] == "IR-9999"


def test_a_conflict_comes_back_as_a_conflict():
    router = APIRouter()

    @router.get("/busy")
    def busy() -> dict[str, str]:
        raise ConflictError("that source is already out", serial="IR-5512")

    response = client(router).get("/busy")
    assert response.status_code == 409
    assert response.json()["context"]["serial"] == "IR-5512"


def test_an_error_that_is_not_ours_is_not_swallowed():
    router = APIRouter()

    @router.get("/other")
    def other() -> dict[str, str]:
        raise RuntimeError("something else")

    with pytest.raises(RuntimeError):
        client(router).get("/other")


def test_a_handler_that_returns_commits_its_unit_of_work():
    router = APIRouter()

    @router.post("/probe/write")
    def make(work: Work) -> dict[str, int]:
        work.jobs.add(
            JobRow(
                year=24,
                serial=1,
                client="c",
                site="s",
                acceptance_code="API 1104",
                procedure_number="RT-001",
                opened_on=__import__("datetime").date(2024, 9, 25),
            )
        )
        work.flush()
        return {"serial": 1}

    @router.get("/probe/count")
    def count(work: Work) -> dict[str, int]:
        return {"count": 1 if work.jobs.find(JobNumber(24, 1)) is not None else 0}

    with TestClient(app_with(router)) as connected:
        assert connected.post("/probe/write").status_code == 200
        assert connected.get("/probe/count").json() == {"count": 1}


def test_a_handler_that_raises_rolls_its_unit_of_work_back():
    router = APIRouter()

    @router.post("/probe/bad")
    def bad(work: Work) -> dict[str, int]:
        work.jobs.add(
            JobRow(
                year=24,
                serial=2,
                client="c",
                site="s",
                acceptance_code="API 1104",
                procedure_number="RT-001",
                opened_on=__import__("datetime").date(2024, 9, 25),
            )
        )
        work.flush()
        raise ConflictError("changed my mind")

    @router.get("/probe/two")
    def two(work: Work) -> dict[str, int]:
        return {"count": 1 if work.jobs.find(JobNumber(24, 2)) is not None else 0}

    with TestClient(app_with(router)) as connected:
        assert connected.post("/probe/bad").status_code == 409
        assert connected.get("/probe/two").json() == {"count": 0}


def test_two_applications_do_not_share_a_database():
    first = TestClient(app_with())
    second = TestClient(app_with())
    assert first.app.state.session_factory is not second.app.state.session_factory


def test_the_openapi_document_names_the_service():
    document = client().get("/openapi.json").json()
    assert document["info"]["title"] == "qu_imager"
    assert document["info"]["version"] == __version__
