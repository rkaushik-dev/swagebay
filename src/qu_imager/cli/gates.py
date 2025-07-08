"""The three things every gate command was doing its own way.

Four commands answer the same shape of question: here is a thing, here is
everything wrong with it, and the shell needs to know whether to carry on. Each
one grew its own copy of the three parts, and they had drifted.

Two of them named an exit constant and two wrote the number four inline. Two
grouped the reasons under headings and two ran them together. All four turned
an optional click date into a day with the same conditional expression, eleven
times across the package, and one of them got it wrong by defaulting to the
epoch instead of today.

So: one exit status, one printer, one day. The printer takes the groups in the
order they should be acted on and prints only the ones with anything in them,
because an empty heading reads as a finding that got lost.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date, datetime

import click

from qu_imager.cli.render import bullets, heading

EXIT_NOT_CLEAR = 4
"""One number, for every gate that answers no."""


def as_day(on: datetime | None) -> date:
    """A click date option as a day, defaulting to today and not to nothing."""
    return on.date() if on is not None else date.today()


def report(
    title: str,
    groups: Sequence[tuple[str, Sequence[str]]],
    clear_message: str = "",
) -> None:
    """Print the reasons under their headings, and exit non zero if there are any.

    Returns normally when everything is clear, so a caller can carry on and
    print whatever else it wanted to. It raises SystemExit otherwise, which is
    what makes these commands usable as the last line of a script.
    """
    found = [(label, list(reasons)) for label, reasons in groups if reasons]
    if not found:
        if clear_message:
            click.echo(clear_message)
        return
    click.echo(heading(title))
    for label, reasons in found:
        if label:
            click.echo(heading(label))
        click.echo(bullets(reasons))
    raise SystemExit(EXIT_NOT_CLEAR)
