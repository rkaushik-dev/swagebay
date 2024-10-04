from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, NotFoundError, ValidationError
from qu_imager.persistence.unit_of_work import (
    build_engine,
    create_schema,
    transaction,
    unit_of_work_factory,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.services.sources import (
    Booking,
    bring_back,
    fitness,
    location,
    record_sighting,
    register_device,
    register_source,
    retire,
    send_out,
)
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import LeakTest, SealedSource, SourceCertificate

ASSAY = datetime(2024, 10, 1)
WENT = date(2024, 10, 7)


def factory():
    engine = build_engine()
    create_schema(engine)
    return unit_of_work_factory(engine)


def a_source(serial="IR-5512", tested=True):
    certificate = SourceCertificate(
        serial=serial,
        isotope=IR192,
        assay_activity=curie(90),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
    )
    source = SealedSource(certificate=certificate, working_floor=curie(15))
    if tested:
        source.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return source


def stocked(work, serial="IR-5512", tested=True):
    register_source(work, a_source(serial, tested))
    register_device(work, "880D-221", "880d")


def a_booking(serial="IR-5512", site="Come By Chance"):
    return Booking(
        source_serial=serial,
        device_serial="880D-221",
        crew_badge="0142",
        site=site,
        went_out=WENT,
    )


def test_a_source_goes_on_the_inventory_once():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        assert work.sources.get("IR-5512").isotope_key == "ir192"
        with pytest.raises(ConflictError):
            register_source(work, a_source())


def test_a_device_goes_on_once_too():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        with pytest.raises(ConflictError):
            register_device(work, "880D-221", "880d")
        with pytest.raises(ValidationError):
            register_device(work, "  ", "880d")


def test_a_source_in_the_shop_reads_as_the_shop():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        assert location(work, "IR-5512") == "shop"


def test_a_source_out_on_a_job_reads_as_the_site():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        assert location(work, "IR-5512") == "Come By Chance"


def test_a_source_cannot_be_out_on_two_jobs():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        with pytest.raises(ConflictError):
            send_out(work, a_booking(site="Somewhere Else"))


def test_a_source_that_is_not_out_cannot_come_back():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        with pytest.raises(ConflictError):
            bring_back(work, "IR-5512", WENT)


def test_bringing_a_source_back_frees_it_for_the_next_job():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        bring_back(work, "IR-5512", WENT + timedelta(days=2), exposures=24)
        assert location(work, "IR-5512") == "shop"
        send_out(work, a_booking(site="Next One"))
        assert location(work, "IR-5512") == "Next One"


def test_a_source_cannot_come_back_before_it_went_out():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        with pytest.raises(ValidationError):
            bring_back(work, "IR-5512", WENT - timedelta(days=1))


def test_a_negative_exposure_count_is_refused():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        with pytest.raises(ValidationError):
            bring_back(work, "IR-5512", WENT, exposures=-1)


def test_a_booking_needs_a_crew_and_a_site():
    with pytest.raises(ValidationError):
        Booking("IR-5512", "880D-221", "  ", "site", WENT)
    with pytest.raises(ValidationError):
        Booking("IR-5512", "880D-221", "0142", "  ", WENT)


def test_a_source_or_device_not_on_the_inventory_cannot_be_booked_out():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        with pytest.raises(NotFoundError):
            send_out(work, a_booking(serial="IR-9999"))


def test_a_fit_source_is_reported_fit():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        assert fitness(work, "IR-5512", ASSAY + timedelta(days=30)).fit


def test_an_untested_source_is_reported_unfit_with_the_reason():
    made = factory()
    with transaction(made) as work:
        stocked(work, tested=False)
        report = fitness(work, "IR-5512", ASSAY)
        assert not report.fit
        assert "leak test is not current" in report.reasons
        assert "leak test" in report.describe()


def test_fitness_depends_on_the_day_it_is_asked_about():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        assert fitness(work, "IR-5512", ASSAY + timedelta(days=30)).fit
        assert not fitness(work, "IR-5512", ASSAY + timedelta(days=300)).fit


def test_a_retired_source_cannot_be_booked_out():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        retire(work, "IR-5512")
        with pytest.raises(ConflictError) as caught:
            send_out(work, a_booking())
        assert caught.value.context["state"] == "awaiting disposal"


def test_a_source_out_on_a_job_cannot_be_retired():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        send_out(work, a_booking())
        with pytest.raises(ConflictError):
            retire(work, "IR-5512")


def test_a_sighting_is_recorded_against_the_source():
    made = factory()
    with transaction(made) as work:
        stocked(work)
        record_sighting(work, "IR-5512", date(2024, 10, 5), "0142", "shop")
        assert len(work.sources.get("IR-5512").sightings) == 1
        with pytest.raises(ValidationError):
            record_sighting(work, "IR-5512", date(2024, 10, 5), "  ", "shop")
