from datetime import date, datetime

from click.testing import CliRunner

from qu_imager.cli.gates import EXIT_NOT_CLEAR, as_day, report


def test_a_day_option_that_was_given_is_that_day():
    assert as_day(datetime(2025, 7, 8, 14, 30)) == date(2025, 7, 8)


def test_a_day_option_left_out_is_today_and_not_the_epoch():
    assert as_day(None) == date.today()


def run(*args, **kwargs):
    import click

    @click.command()
    def command():
        report(*args, **kwargs)

    return CliRunner().invoke(command, [], standalone_mode=False)


def test_nothing_wrong_prints_the_clear_message_and_exits_zero():
    result = run("title", (("group", ()),), clear_message="all good")
    assert result.exit_code == 0
    assert result.output.strip() == "all good"


def test_nothing_wrong_and_no_message_prints_nothing():
    result = run("title", (("group", ()),))
    assert result.exit_code == 0
    assert result.output == ""


def test_reasons_are_printed_under_their_headings_and_exit_four():
    result = run("not clear", (("source", ("wipe test",)), ("device", ("no quarterly",))))
    assert result.exit_code == EXIT_NOT_CLEAR
    assert "not clear" in result.output
    assert "source" in result.output
    assert "wipe test" in result.output
    assert "no quarterly" in result.output


def test_an_empty_group_is_not_printed_as_a_heading():
    result = run("not clear", (("source", ()), ("device", ("no quarterly",))))
    assert "source" not in result.output
    assert "device" in result.output


def test_a_group_with_no_label_prints_the_reasons_alone():
    result = run("not clear", (("", ("NCR-1 is still open",)),))
    assert "NCR-1 is still open" in result.output
    assert result.exit_code == EXIT_NOT_CLEAR


def test_the_clear_message_is_not_printed_when_something_is_wrong():
    result = run("not clear", (("device", ("no quarterly",)),), clear_message="all good")
    assert "all good" not in result.output


def test_every_gate_in_the_package_uses_the_one_exit_status():
    import qu_imager.cli.devices as devices
    import qu_imager.cli.history as history
    import qu_imager.cli.nonconformance as ncr
    import qu_imager.cli.store as store

    for module in (devices, history, ncr, store):
        assert not hasattr(module, "EXIT_NOT_CLEARED")
        assert not hasattr(module, "EXIT_STORE_MISMATCH")
    assert EXIT_NOT_CLEAR == 4
