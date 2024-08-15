from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from qu_imager.errors import ConfigurationError
from qu_imager.persistence.base import Base
from qu_imager.persistence.models import SourceRow
from qu_imager.persistence.source_mapper import (
    row_to_source,
    source_to_row,
    state_from_value,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.sources.isotope import IR192, SE75
from qu_imager.sources.sealed import (
    LeakTest,
    SealedSource,
    SourceCertificate,
    SourceState,
)

ASSAY = datetime(2024, 8, 1, tzinfo=UTC)
NAIVE_ASSAY = datetime(2024, 8, 1)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def a_source(serial="IR-5512", made_of=IR192, state=SourceState.IN_SERVICE, tests=1):
    certificate = SourceCertificate(
        serial=serial,
        isotope=made_of,
        assay_activity=curie(90),
        assay_at=NAIVE_ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm("3.5"),
    )
    source = SealedSource(certificate=certificate, state=state, working_floor=curie(15))
    for index in range(tests):
        source.leak_tests.append(
            LeakTest(NAIVE_ASSAY + timedelta(days=index * 30), Decimal(index), f"rp{index}")
        )
    return source


def stored(source):
    with session() as db:
        db.add(source_to_row(source))
        db.commit()
        return row_to_source(db.scalars(select(SourceRow)).one())


def test_a_source_survives_the_database():
    original = a_source()
    recovered = stored(original)
    assert recovered.serial == original.serial
    assert recovered.isotope is IR192
    assert recovered.certificate.assay_activity == curie(90)


def test_the_pellet_dimensions_come_back_the_right_way_round():
    recovered = stored(a_source())
    assert recovered.certificate.active_diameter == mm(3)
    assert recovered.certificate.active_length == mm("3.5")
    assert recovered.certificate.focal_size == mm("3.5")


def test_the_working_floor_is_not_confused_with_the_assay():
    recovered = stored(a_source())
    assert recovered.working_floor == curie(15)
    assert recovered.certificate.assay_activity == curie(90)


def test_the_state_survives_as_the_phrase_a_human_reads():
    row = source_to_row(a_source(state=SourceState.AWAITING_DISPOSAL))
    assert row.state == "awaiting disposal"


def test_every_state_survives_the_round_trip():
    for state in SourceState:
        assert stored(a_source(state=state)).state is state


def test_a_stored_state_that_is_not_a_state_is_refused():
    with pytest.raises(ConfigurationError):
        state_from_value("mislaid")
    assert state_from_value("in transit") is SourceState.IN_TRANSIT


def test_the_leak_tests_come_back_in_full():
    recovered = stored(a_source(tests=3))
    assert len(recovered.leak_tests) == 3
    assert recovered.latest_leak_test().technician == "rp2"


def test_a_source_with_no_leak_tests_round_trips_as_one():
    recovered = stored(a_source(tests=0))
    assert recovered.leak_tests == []
    assert recovered.latest_leak_test() is None


def test_a_failed_wipe_result_survives_as_a_decimal():
    source = a_source(tests=0)
    source.leak_tests.append(LeakTest(NAIVE_ASSAY, Decimal("204.5"), "rp"))
    recovered = stored(source)
    assert recovered.leak_tests[0].becquerels_removed == Decimal("204.5")
    assert not recovered.leak_tests[0].passed


def test_a_different_isotope_comes_back_as_that_isotope():
    assert stored(a_source(made_of=SE75)).isotope is SE75


def test_the_recovered_source_still_decays_correctly():
    recovered = stored(a_source())
    later = recovered.activity_at(NAIVE_ASSAY + timedelta(days=74))
    assert Decimal("44") < later.curie < Decimal("46")


def test_the_row_carries_the_isotope_key_and_not_the_object():
    row = source_to_row(a_source())
    assert row.isotope_key == "ir192"
    assert isinstance(row.isotope_key, str)


def test_two_sources_can_be_stored_side_by_side():
    with session() as db:
        db.add(source_to_row(a_source("IR-5512")))
        db.add(source_to_row(a_source("SE-2211", made_of=SE75)))
        db.commit()
        recovered = [row_to_source(r) for r in db.scalars(select(SourceRow)).all()]
        assert {s.serial for s in recovered} == {"IR-5512", "SE-2211"}
        assert {s.isotope.key for s in recovered} == {"ir192", "se75"}


def test_the_assay_instant_is_not_shifted_by_the_round_trip():
    recovered = stored(a_source())
    assert recovered.certificate.assay_at == NAIVE_ASSAY
    assert ASSAY.date() == recovered.certificate.assay_at.date()
