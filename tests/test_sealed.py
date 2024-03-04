from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.length import mm
from qu_imager.sources.isotope import CO60, IR192
from qu_imager.sources.sealed import (
    LeakTest,
    SealedSource,
    SourceCertificate,
    SourceState,
)

ASSAY = datetime(2024, 3, 1, tzinfo=UTC)
FULL_LOAD = curie(100)


def certificate(serial="IR-4471", activity=FULL_LOAD, diameter="3.0", length="3.0"):
    return SourceCertificate(
        serial=serial,
        isotope=IR192,
        assay_activity=activity,
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(diameter),
        active_length=mm(length),
    )


def source(**kwargs):
    src = SealedSource(certificate=certificate(**kwargs))
    src.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    return src


def test_the_certificate_carries_the_pellet_size_the_geometry_needs():
    cert = certificate(diameter="3.0", length="4.5")
    assert cert.focal_size == mm("4.5")
    assert certificate(diameter="4.0", length="2.0").focal_size == mm("4.0")


def test_a_certificate_without_a_serial_or_a_pellet_is_refused():
    with pytest.raises(ValidationError):
        certificate(serial="   ")
    with pytest.raises(ValidationError):
        certificate(diameter="0")
    with pytest.raises(ValidationError):
        certificate(activity=Activity.ZERO)


def test_the_source_decays_on_its_own_certificate():
    src = source()
    assert src.activity_at(ASSAY) == curie(100)
    later = src.activity_at(ASSAY + timedelta(days=IR192.half_life_days.__int__()))
    assert Decimal("50") < later.curie < Decimal("51")


def test_a_fresh_source_with_a_current_leak_test_is_fit():
    src = source()
    assert src.is_fit_at(ASSAY + timedelta(days=30))
    assert src.reasons_not_to_use(ASSAY + timedelta(days=30)) == ()


def test_a_leak_test_expires_after_six_months():
    src = source()
    assert src.leak_test_current_at(ASSAY + timedelta(days=179))
    assert not src.leak_test_current_at(ASSAY + timedelta(days=181))
    assert "leak test is not current" in src.reasons_not_to_use(ASSAY + timedelta(days=181))


def test_a_source_with_no_leak_test_at_all_is_not_fit():
    src = SealedSource(certificate=certificate())
    assert src.latest_leak_test() is None
    assert "leak test is not current" in src.reasons_not_to_use(ASSAY)


def test_a_failed_wipe_puts_the_source_straight_into_disposal():
    src = source()
    src.record_leak_test(LeakTest(ASSAY + timedelta(days=10), Decimal(400), "rp"))
    assert src.state is SourceState.AWAITING_DISPOSAL
    reasons = src.reasons_not_to_use(ASSAY + timedelta(days=11))
    assert "source is awaiting disposal" in reasons
    assert "leak test is not current" in reasons


def test_the_wipe_limit_is_one_hundred_and_eighty_five_becquerels():
    assert LeakTest(ASSAY, Decimal(184), "rp").passed
    assert not LeakTest(ASSAY, Decimal(185), "rp").passed
    assert not LeakTest(ASSAY, Decimal(1000), "rp").passed


def test_the_latest_wipe_is_the_one_that_counts():
    src = source()
    src.record_leak_test(LeakTest(ASSAY + timedelta(days=170), Decimal(2), "rp"))
    assert src.leak_test_current_at(ASSAY + timedelta(days=300))
    assert src.latest_leak_test().performed_at == ASSAY + timedelta(days=170)


def test_a_source_below_the_working_floor_stays_in_the_pig():
    src = source()
    src.working_floor = curie(60)
    late = ASSAY + timedelta(days=80)
    assert "activity is below the working floor" in src.reasons_not_to_use(late)


def test_transit_and_disposal_states_block_use_and_stored_does_not():
    assert SourceState.IN_SERVICE.can_be_used
    assert SourceState.STORED.can_be_used
    assert not SourceState.IN_TRANSIT.can_be_used
    assert not SourceState.DISPOSED.can_be_used
    src = source()
    src.state = SourceState.IN_TRANSIT
    assert not src.is_fit_at(ASSAY)


def test_every_reason_is_reported_not_just_the_first():
    src = SealedSource(certificate=certificate(), state=SourceState.DISPOSED)
    src.working_floor = curie(500)
    assert len(src.reasons_not_to_use(ASSAY)) == 3


def test_a_source_prints_as_the_isotope_and_the_serial():
    assert str(source()) == "Ir-192 IR-4471"
    assert source().isotope is IR192
    assert CO60 is not IR192
