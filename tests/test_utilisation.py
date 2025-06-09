from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.safety.inventory import DeviceUnit, Trip
from qu_imager.safety.utilisation import RETENTION_DAYS, Use, UtilisationLog
from qu_imager.sources.device import device_model
from qu_imager.sources.exchange import Exchange, Loading

MONDAY = date(2025, 6, 9)
TUESDAY = MONDAY + timedelta(days=1)
CAMERA = DeviceUnit(serial="D-77", model=device_model("880d"))


def a_use(
    on=MONDAY,
    device="D-77",
    source="IR-5512",
    site="Come By Chance",
    crew=("0142",),
    exposures=14,
):
    return Use(
        used_on=on,
        device_serial=device,
        source_serial=source,
        site=site,
        radiographers=crew,
        exposures=exposures,
    )


def test_a_use_names_everything_the_condition_asks_for():
    use = a_use()
    assert use.used_on == MONDAY
    assert use.device_serial == "D-77"
    assert use.source_serial == "IR-5512"
    assert use.radiographers == ("0142",)


def test_a_use_with_nobody_on_it_is_refused():
    with pytest.raises(ValidationError):
        a_use(crew=())


def test_a_blank_badge_is_not_a_radiographer():
    with pytest.raises(ValidationError):
        a_use(crew=("0142", "  "))


def test_the_same_badge_twice_on_one_entry_is_refused():
    with pytest.raises(ValidationError):
        a_use(crew=("0142", "0142"))


def test_a_use_with_no_device_or_no_source_is_refused():
    with pytest.raises(ValidationError):
        a_use(device="  ")
    with pytest.raises(ValidationError):
        a_use(source="  ")


def test_a_use_that_does_not_say_where_is_refused():
    with pytest.raises(ValidationError):
        a_use(site="   ")


def test_negative_exposures_are_refused():
    with pytest.raises(ValidationError):
        a_use(exposures=-1)


def test_a_day_out_with_nothing_shot_is_still_a_use():
    use = a_use(exposures=0)
    assert use.was_a_mobilisation
    assert not a_use().was_a_mobilisation


def test_an_entry_prints_the_way_an_inspector_reads_it():
    text = a_use(crew=("0201", "0142")).describe()
    assert "D-77 with IR-5512" in text
    assert "14 exposures" in text
    assert text.endswith("0142, 0201")


def test_the_log_keeps_what_was_recorded():
    log = UtilisationLog()
    log.record(a_use())
    assert len(log.for_device("D-77", MONDAY, TUESDAY)) == 1


def test_a_use_naming_a_source_the_device_was_not_holding_is_refused():
    loading = Loading()
    loading.history.append(Exchange("D-77", None, "IR-5512", MONDAY, "0142", "QSA-1"))
    loading.held["D-77"] = "IR-5512"
    log = UtilisationLog()
    with pytest.raises(ConflictError) as caught:
        log.record(a_use(source="IR-9999"), loading)
    assert caught.value.context["holding"] == "IR-5512"


def test_a_use_agreeing_with_the_loading_record_goes_in():
    loading = Loading()
    loading.held["D-77"] = "IR-5512"
    log = UtilisationLog()
    log.record(a_use(), loading)
    assert len(log.uses) == 1


def test_a_device_the_loading_record_knows_nothing_about_is_not_second_guessed():
    log = UtilisationLog()
    log.record(a_use(), Loading())
    assert len(log.uses) == 1


def test_one_device_over_a_window_reads_in_date_order():
    log = UtilisationLog()
    log.record(a_use(on=TUESDAY))
    log.record(a_use(on=MONDAY))
    days = [u.used_on for u in log.for_device("D-77", MONDAY, TUESDAY)]
    assert days == [MONDAY, TUESDAY]


def test_a_window_that_ends_before_the_entry_does_not_include_it():
    log = UtilisationLog()
    log.record(a_use(on=TUESDAY))
    assert log.for_device("D-77", MONDAY, MONDAY) == ()


def test_one_source_can_be_followed_across_devices():
    log = UtilisationLog()
    log.record(a_use())
    log.record(a_use(device="D-78", on=TUESDAY))
    assert len(log.for_source("IR-5512", MONDAY, TUESDAY)) == 2


def test_one_badge_can_be_followed_across_sites():
    log = UtilisationLog()
    log.record(a_use(crew=("0142", "0201")))
    log.record(a_use(on=TUESDAY, site="Point Tupper", crew=("0142",)))
    assert log.sites_worked("0142", MONDAY, TUESDAY) == ("Come By Chance", "Point Tupper")
    assert log.sites_worked("0201", MONDAY, TUESDAY) == ("Come By Chance",)


def test_two_entries_on_one_day_count_as_one_day_used():
    log = UtilisationLog()
    log.record(a_use())
    log.record(a_use(site="Point Tupper"))
    assert log.days_used("D-77", MONDAY, TUESDAY) == 1


def test_exposures_add_up_per_device():
    log = UtilisationLog()
    log.record(a_use(exposures=14))
    log.record(a_use(on=TUESDAY, exposures=6))
    log.record(a_use(device="D-78", exposures=3))
    counted = log.exposures_by_device(MONDAY, TUESDAY)
    assert counted == {"D-77": 20, "D-78": 3}


def test_a_day_the_device_was_out_with_nothing_logged_is_found():
    log = UtilisationLog()
    log.record(a_use())
    trip = Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY)
    assert log.unlogged_days([trip]) == (("D-77", TUESDAY),)


def test_a_fully_logged_trip_has_no_gaps():
    log = UtilisationLog()
    log.record(a_use())
    log.record(a_use(on=TUESDAY))
    trip = Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY)
    assert log.unlogged_days([trip]) == ()


def test_a_trip_still_out_is_checked_up_to_the_day_it_left():
    log = UtilisationLog()
    trip = Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY)
    assert log.unlogged_days([trip]) == (("D-77", MONDAY),)


def test_an_entry_falls_out_of_retention_after_three_years():
    use = a_use()
    assert use.destroy_after() == MONDAY + timedelta(days=RETENTION_DAYS)
    log = UtilisationLog()
    log.record(use)
    assert log.due_for_destruction(use.destroy_after()) == (use,)
    assert log.due_for_destruction(use.destroy_after() - timedelta(days=1)) == ()


def test_entries_after_a_date_can_be_pulled_for_a_late_amendment():
    log = UtilisationLog()
    log.record(a_use())
    log.record(a_use(on=TUESDAY))
    assert len(log.entries_after(MONDAY)) == 1
