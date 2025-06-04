from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, ConflictError, ValidationError
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.safety.inventory import DeviceUnit
from qu_imager.sources.device import device_model
from qu_imager.sources.exchange import Exchange, Loading, activity_lost
from qu_imager.sources.isotope import CO60, IR192
from qu_imager.sources.sealed import (
    LeakTest,
    SealedSource,
    SourceCertificate,
    SourceState,
)

ASSAY = datetime(2025, 4, 1, tzinfo=UTC)
SWAP = date(2025, 6, 4)
CAMERA = DeviceUnit(serial="D-77", model=device_model("880d"))
COBALT_CAMERA = DeviceUnit(serial="D-99", model=device_model("tk100"))


def a_source(serial="IR-5512", activity=90, isotope=IR192, wiped=ASSAY):
    certificate = SourceCertificate(
        serial=serial,
        isotope=isotope,
        assay_activity=curie(activity),
        assay_at=ASSAY,
        manufacturer="QSA",
        model="A424-9",
        active_diameter=mm(3),
        active_length=mm(3),
    )
    source = SealedSource(certificate=certificate)
    if wiped is not None:
        source.record_leak_test(LeakTest(wiped, Decimal(0), "rp"))
    return source


def an_exchange(removed=None, installed="IR-5512", device="D-77", on=SWAP):
    return Exchange(
        device_serial=device,
        removed_serial=removed,
        installed_serial=installed,
        performed_on=on,
        performed_by="0142",
        shipment="QSA-88213",
    )


def test_an_exchange_that_moved_nothing_is_refused():
    with pytest.raises(ValidationError):
        an_exchange(removed=None, installed=None)


def test_an_exchange_nobody_signed_is_refused():
    with pytest.raises(ValidationError):
        Exchange("D-77", None, "IR-5512", SWAP, "  ", "QSA-88213")


def test_an_exchange_with_no_shipment_is_refused():
    with pytest.raises(ValidationError):
        Exchange("D-77", None, "IR-5512", SWAP, "0142", "   ")


def test_the_same_source_out_and_in_is_refused():
    with pytest.raises(ValidationError):
        an_exchange(removed="IR-5512", installed="IR-5512")


def test_a_load_reads_as_a_load():
    assert an_exchange().is_load
    assert not an_exchange().is_unload


def test_an_unload_reads_as_an_unload():
    assert an_exchange(removed="IR-5512", installed=None).is_unload


def test_each_kind_of_exchange_says_what_it_did():
    assert "into D-77" in an_exchange().describe()
    assert "out of D-77" in an_exchange(removed="IR-5512", installed=None).describe()
    assert "out and" in an_exchange(removed="IR-4400", installed="IR-5512").describe()


def test_a_load_puts_the_source_in_the_device():
    loading = Loading()
    source = a_source()
    loading.apply(an_exchange(), CAMERA, installed=source)
    assert loading.source_in("D-77") == "IR-5512"
    assert loading.device_holding("IR-5512") == "D-77"
    assert source.state is SourceState.IN_SERVICE


def test_a_device_nobody_loaded_holds_nothing():
    assert Loading().source_in("D-77") is None
    assert Loading().device_holding("IR-5512") is None


def test_a_second_load_without_an_unload_is_refused():
    loading = Loading()
    loading.apply(an_exchange(), CAMERA, installed=a_source())
    with pytest.raises(ConflictError):
        loading.apply(an_exchange(installed="IR-6000"), CAMERA, installed=a_source("IR-6000"))


def test_the_refused_load_leaves_the_device_as_it_was():
    loading = Loading()
    loading.apply(an_exchange(), CAMERA, installed=a_source())
    with pytest.raises(ConflictError):
        loading.apply(an_exchange(installed="IR-6000"), CAMERA, installed=a_source("IR-6000"))
    assert loading.source_in("D-77") == "IR-5512"
    assert len(loading.history) == 1


def test_an_unload_sends_the_source_into_transit():
    loading = Loading()
    old = a_source()
    loading.apply(an_exchange(), CAMERA, installed=old)
    loading.apply(an_exchange(removed="IR-5512", installed=None), CAMERA, removed=old)
    assert loading.source_in("D-77") is None
    assert old.state is SourceState.IN_TRANSIT


def test_a_swap_does_both_halves_at_once():
    loading = Loading()
    old, new = a_source(), a_source("IR-6000")
    loading.apply(an_exchange(), CAMERA, installed=old)
    loading.apply(
        an_exchange(removed="IR-5512", installed="IR-6000"), CAMERA, removed=old, installed=new
    )
    assert loading.source_in("D-77") == "IR-6000"
    assert old.state is SourceState.IN_TRANSIT
    assert new.state is SourceState.IN_SERVICE


def test_taking_out_a_source_the_device_does_not_hold_is_refused():
    loading = Loading()
    loading.apply(an_exchange(), CAMERA, installed=a_source())
    with pytest.raises(ConflictError) as caught:
        loading.apply(an_exchange(removed="IR-9999", installed=None), CAMERA)
    assert caught.value.context["holding"] == "IR-5512"


def test_a_source_already_in_another_device_cannot_be_loaded_again():
    loading = Loading()
    loading.apply(an_exchange(), CAMERA, installed=a_source())
    other = DeviceUnit(serial="D-78", model=device_model("880s"))
    with pytest.raises(ConflictError):
        loading.apply(an_exchange(device="D-78"), other, installed=a_source())


def test_an_exchange_against_a_different_device_is_refused():
    with pytest.raises(ValidationError):
        Loading().apply(an_exchange(device="D-78"), CAMERA)


def test_a_source_that_is_not_the_one_named_is_refused():
    with pytest.raises(ValidationError):
        Loading().apply(an_exchange(), CAMERA, installed=a_source("IR-9999"))


def test_a_source_with_no_current_wipe_test_does_not_go_in():
    stale = a_source(wiped=ASSAY - timedelta(days=400))
    with pytest.raises(ConflictError):
        Loading().apply(an_exchange(), CAMERA, installed=stale)


def test_a_source_awaiting_disposal_does_not_go_in():
    failed = a_source(wiped=None)
    failed.record_leak_test(LeakTest(ASSAY - timedelta(days=1), Decimal(400), "rp"))
    failed.record_leak_test(LeakTest(ASSAY, Decimal(0), "rp"))
    with pytest.raises(ConflictError) as caught:
        Loading().apply(an_exchange(), CAMERA, installed=failed)
    assert "awaiting disposal" in str(caught.value)


def test_the_wrong_isotope_for_the_device_is_refused():
    cobalt = a_source("CO-100", activity=20, isotope=CO60)
    with pytest.raises(ConfigurationError):
        Loading().apply(an_exchange(installed="CO-100"), CAMERA, installed=cobalt)


def test_more_curies_than_the_certificate_covers_is_refused():
    with pytest.raises(ConfigurationError):
        Loading().apply(an_exchange(), CAMERA, installed=a_source(activity=400))


def test_a_cobalt_source_goes_into_the_cobalt_device():
    cobalt = a_source("CO-100", activity=20, isotope=CO60)
    loading = Loading()
    loading.apply(an_exchange(installed="CO-100", device="D-99"), COBALT_CAMERA, installed=cobalt)
    assert loading.source_in("D-99") == "CO-100"


def test_the_history_of_one_device_reads_in_date_order():
    loading = Loading()
    old = a_source()
    new = a_source("IR-6000", wiped=ASSAY + timedelta(days=200))
    loading.apply(an_exchange(), CAMERA, installed=old)
    later = an_exchange(removed="IR-5512", installed="IR-6000", on=SWAP + timedelta(days=180))
    loading.apply(later, CAMERA, removed=old, installed=new)
    dates = [e.performed_on for e in loading.exchanges_for("D-77")]
    assert dates == sorted(dates)
    assert loading.exchanges_for("D-78") == ()


def test_the_loaded_devices_are_the_ones_holding_something():
    loading = Loading()
    loading.apply(an_exchange(), CAMERA, installed=a_source())
    assert loading.loaded_devices() == ("D-77",)


def test_the_activity_a_device_used_up_is_the_decay_over_its_time_in():
    lost = activity_lost(a_source(), ASSAY.date(), ASSAY.date() + timedelta(days=74))
    assert curie(44) < lost < curie(46)


def test_no_time_in_the_device_loses_nothing():
    assert activity_lost(a_source(), ASSAY.date(), ASSAY.date()) == curie(0)


def test_coming_out_before_going_in_is_refused():
    with pytest.raises(ValidationError):
        activity_lost(a_source(), ASSAY.date(), ASSAY.date() - timedelta(days=1))
