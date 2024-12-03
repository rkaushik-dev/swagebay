from datetime import date, timedelta

import pytest

from qu_imager.calibration import Certificate, Instrument, Item, Register
from qu_imager.errors import ConflictError, ValidationError

DONE = date(2024, 6, 1)


def certificate(done=DONE, due=None, lab="NRC"):
    return Certificate(
        calibrated_on=done,
        due_on=due or done + timedelta(days=365),
        laboratory=lab,
        reference="C-1188",
    )


def register():
    reg = Register()
    reg.add(Item("LU-3-1188", Instrument.SURVEY_METER))
    reg.add(Item("XR-301", Instrument.DENSITOMETER))
    reg.add(Item("SW-14", Instrument.STEP_WEDGE))
    return reg


def test_an_instrument_with_no_certificate_is_overdue_rather_than_unknown():
    reg = register()
    assert reg.overdue_on(DONE) == ("LU-3-1188", "SW-14", "XR-301")
    assert reg.get("XR-301").due_on() is None


def test_a_calibrated_instrument_drops_off_the_overdue_list():
    reg = register()
    reg.calibrate("XR-301", certificate())
    assert "XR-301" not in reg.overdue_on(DONE + timedelta(days=30))


def test_it_comes_back_when_the_certificate_runs_out():
    reg = register()
    reg.calibrate("XR-301", certificate())
    assert "XR-301" not in reg.overdue_on(DONE + timedelta(days=364))
    assert "XR-301" in reg.overdue_on(DONE + timedelta(days=365))


def test_the_certificate_date_wins_over_the_interval():
    reg = register()
    short = certificate(due=DONE + timedelta(days=90))
    reg.calibrate("XR-301", short)
    assert reg.get("XR-301").due_on() == DONE + timedelta(days=90)
    assert reg.default_due("XR-301", DONE) == DONE + timedelta(days=365)


def test_the_latest_certificate_is_the_one_that_counts():
    reg = register()
    reg.calibrate("XR-301", certificate(done=DONE))
    reg.calibrate("XR-301", certificate(done=DONE + timedelta(days=300)))
    assert reg.get("XR-301").due_on() == DONE + timedelta(days=665)


def test_a_certificate_that_expires_before_it_was_issued_is_refused():
    with pytest.raises(ValidationError):
        Certificate(DONE, DONE, "NRC", "C-1")
    with pytest.raises(ValidationError):
        Certificate(DONE, DONE - timedelta(days=1), "NRC", "C-1")


def test_a_certificate_names_the_laboratory():
    with pytest.raises(ValidationError):
        certificate(lab="  ")


def test_an_instrument_needs_a_serial():
    with pytest.raises(ValidationError):
        Item("  ", Instrument.SURVEY_METER)


def test_nothing_goes_on_the_register_twice():
    reg = register()
    with pytest.raises(ConflictError):
        reg.add(Item("XR-301", Instrument.DENSITOMETER))


def test_an_instrument_that_is_not_registered_cannot_be_calibrated():
    reg = register()
    with pytest.raises(ConflictError):
        reg.calibrate("XR-999", certificate())


def test_the_intervals_are_not_all_the_same():
    assert Instrument.SURVEY_METER.default_interval == timedelta(days=365)
    assert Instrument.STEP_WEDGE.default_interval == timedelta(days=730)
    assert Instrument.VIEWER.default_interval > Instrument.DOSIMETER.default_interval


def test_a_step_wedge_is_replaced_rather_than_calibrated():
    assert Instrument.STEP_WEDGE.is_replaced_rather_than_calibrated
    assert not Instrument.SURVEY_METER.is_replaced_rather_than_calibrated


def test_the_look_ahead_lists_what_can_still_be_booked():
    reg = register()
    reg.calibrate("XR-301", certificate())
    day = DONE + timedelta(days=340)
    assert reg.due_within(day, 30) == ("XR-301",)
    assert reg.due_within(day, 10) == ()


def test_something_already_overdue_is_not_on_the_look_ahead():
    reg = register()
    reg.calibrate("XR-301", certificate())
    late = DONE + timedelta(days=400)
    assert "XR-301" in reg.overdue_on(late)
    assert reg.due_within(late, 30) == ()


def test_a_negative_look_ahead_is_refused():
    with pytest.raises(ValidationError):
        register().due_within(DONE, -1)


def test_the_days_left_run_down_and_go_negative():
    reg = register()
    reg.calibrate("XR-301", certificate())
    assert reg.get("XR-301").days_left_on(DONE) == 365
    assert reg.get("XR-301").days_left_on(DONE + timedelta(days=400)) == -35
    assert reg.get("SW-14").days_left_on(DONE) is None


def test_an_instrument_prints_as_the_thing_it_is():
    assert str(Instrument.SURVEY_METER) == "survey meter"
    assert str(Instrument.ALARM) == "alarming ratemeter"
