import pytest

from qu_imager.cli.render import bullets, heading, pairs, table, widths, yes_no
from qu_imager.errors import ValidationError


def test_widths_come_from_the_longest_cell():
    assert widths([["a", "bbb"], ["cc", "d"]]) == (2, 3)
    assert widths([]) == ()


def test_a_ragged_table_is_refused():
    with pytest.raises(ValidationError) as caught:
        widths([["a", "b"], ["c"]])
    assert caught.value.context["expected"] == 2


def test_a_table_lines_its_columns_up():
    rendered = table(["weld", "exposures"], [["GW-104", "8"], ["GW-1104", "3"]])
    lines = rendered.splitlines()
    assert lines[0] == "weld     exposures"
    assert lines[1] == "-------  ---------"
    assert lines[2] == "GW-104   8"


def test_numeric_columns_are_right_aligned():
    rendered = table(["weld", "n"], [["GW-104", "8"], ["GW-1104", "12"]], numeric=[1])
    lines = rendered.splitlines()
    assert lines[2].endswith(" 8")
    assert lines[3].endswith("12")


def test_a_table_with_no_rows_still_prints_its_headings():
    rendered = table(["weld", "n"], [])
    assert rendered.splitlines()[0] == "weld  n"
    assert len(rendered.splitlines()) == 2


def test_a_table_with_no_headings_is_refused():
    with pytest.raises(ValidationError):
        table([], [["a"]])


def test_nothing_in_the_output_needs_a_terminal_to_read():
    rendered = table(["a"], [["b"]])
    assert "\x1b" not in rendered
    assert all(ord(c) < 128 for c in rendered)


def test_trailing_padding_is_trimmed_so_lines_do_not_carry_spaces():
    rendered = table(["weld", "note"], [["GW-104", ""]])
    assert not rendered.splitlines()[2].endswith(" ")


def test_a_pair_block_lines_the_labels_up():
    rendered = pairs([("shot", "panoramic"), ("exposures", "1")])
    assert rendered.splitlines()[0] == "shot       panoramic"
    assert rendered.splitlines()[1] == "exposures  1"


def test_an_empty_pair_block_is_empty():
    assert pairs([]) == ""


def test_bullets_take_a_marker():
    assert bullets(["one", "two"]) == "- one\n- two"
    assert bullets(["one"], marker="*") == "* one"
    assert bullets([]) == ""


def test_a_heading_is_underlined_to_its_own_width():
    assert heading("technique sheet") == "technique sheet\n==============="


def test_yes_and_no_are_words_rather_than_symbols():
    assert yes_no(True) == "yes"
    assert yes_no(False) == "no"
