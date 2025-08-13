from datetime import date, timedelta

import pytest

from qu_imager.errors import ValidationError
from qu_imager.persistence.unit_of_work import UnitOfWork, transaction
from qu_imager.safety.utilisation import Use
from qu_imager.sources.exchange import Exchange

from conftest import database

SWAP = date(2025, 8, 13)
MONDAY = date(2025, 8, 18)
TUESDAY = MONDAY + timedelta(days=1)


def an_exchange(removed=None, installed="IR-5512", device="D-77", on=SWAP, shipment="QSA-88213"):
    return Exchange(
        device_serial=device,
        removed_serial=removed,
        installed_serial=installed,
        performed_on=on,
        performed_by="0142",
        shipment=shipment,
    )


def a_use(on=MONDAY, device="D-77", source="IR-5512", site="Come By Chance", crew=("0142",)):
    return Use(
        used_on=on,
        device_serial=device,
        source_serial=source,
        site=site,
        radiographers=crew,
        exposures=14,
    )


def test_an_exchange_comes_back_the_way_it_went_in():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        found = work.shop.exchanges_for("D-77")
    assert len(found) == 1
    assert found[0].installed_serial == "IR-5512"
    assert found[0].shipment == "QSA-88213"


def test_another_devices_exchanges_are_not_in_this_history():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        work.shop.record_exchange(an_exchange(device="D-78"))
        assert len(work.shop.exchanges_for("D-77")) == 1


def test_one_shipment_can_carry_both_halves_of_a_swap():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        work.shop.record_exchange(an_exchange(device="D-78", installed="IR-6000"))
        assert len(work.shop.exchanges_on("QSA-88213")) == 2


def test_the_loading_is_replayed_from_the_exchanges():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        assert work.shop.loading().source_in("D-77") == "IR-5512"


def test_an_unload_leaves_the_device_empty():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        work.shop.record_exchange(
            an_exchange(removed="IR-5512", installed=None, on=SWAP + timedelta(days=180))
        )
        assert work.shop.loading().source_in("D-77") is None


def test_a_swap_leaves_the_new_source_in():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(an_exchange())
        work.shop.record_exchange(
            an_exchange(removed="IR-5512", installed="IR-6000", on=SWAP + timedelta(days=180))
        )
        loading = work.shop.loading()
    assert loading.source_in("D-77") == "IR-6000"
    assert loading.device_holding("IR-6000") == "D-77"


def test_the_replay_reads_in_date_order_whatever_order_it_was_written():
    with UnitOfWork(database()) as work:
        work.shop.record_exchange(
            an_exchange(removed="IR-5512", installed="IR-6000", on=SWAP + timedelta(days=180))
        )
        work.shop.record_exchange(an_exchange())
        assert work.shop.loading().source_in("D-77") == "IR-6000"


def test_a_use_comes_back_with_its_crew():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use(crew=("0142", "0201")))
        found = work.shop.uses_between(MONDAY, TUESDAY)
    assert found[0].radiographers == ("0142", "0201")
    assert found[0].exposures == 14


def test_a_window_that_ends_before_the_entry_does_not_include_it():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use(on=TUESDAY))
        assert work.shop.uses_between(MONDAY, MONDAY) == ()


def test_the_same_device_at_the_same_site_on_the_same_day_goes_in_once():
    from sqlalchemy.exc import IntegrityError

    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        work.shop.record_use(a_use())
        with pytest.raises(IntegrityError):
            work.flush()


def test_the_same_device_at_two_sites_in_one_day_is_two_entries():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        work.shop.record_use(a_use(site="Point Tupper"))
        assert len(work.shop.uses_between(MONDAY, MONDAY)) == 2


def test_the_log_answers_the_domain_questions():
    from qu_imager.safety.inventory import Trip

    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        log = work.shop.log_between(MONDAY, TUESDAY)
    trip = Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY)
    assert log.unlogged_days([trip]) == (("D-77", TUESDAY),)
    assert log.days_used("D-77", MONDAY, TUESDAY) == 1


def test_one_entry_can_be_found_by_device_day_and_site():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        work.flush()
        assert work.shop.use_on("D-77", MONDAY, "Come By Chance").exposures == 14
        assert work.shop.use_on("D-77", TUESDAY, "Come By Chance") is None


def test_exposures_can_be_topped_up_when_the_films_are_counted():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        work.flush()
        found = work.shop.add_exposures("D-77", MONDAY, "Come By Chance", 6)
    assert found.exposures == 20


def test_topping_up_a_day_nobody_logged_is_refused():
    with UnitOfWork(database()) as work, pytest.raises(ValidationError):
        work.shop.add_exposures("D-77", MONDAY, "Come By Chance", 6)


def test_a_negative_top_up_is_refused():
    with UnitOfWork(database()) as work:
        work.shop.record_use(a_use())
        work.flush()
        with pytest.raises(ValidationError):
            work.shop.add_exposures("D-77", MONDAY, "Come By Chance", -1)


def test_both_registers_survive_the_transaction_that_wrote_them():
    made = database()
    with transaction(made) as work:
        work.shop.record_exchange(an_exchange())
        work.shop.record_use(a_use())
    with UnitOfWork(made) as work:
        assert work.shop.loading().source_in("D-77") == "IR-5512"
        assert len(work.shop.uses_between(MONDAY, TUESDAY)) == 1
