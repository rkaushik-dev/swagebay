"""Film stock, which runs out at two in the morning three hundred miles away.

Film has an expiry date and it is not advisory. Fogging climbs slowly for
months and then quickly, and a box a fortnight past its date produces film that
passes the density check and fails the indicator, which costs a reshoot and an
argument. So expired stock is not issued, and stock about to expire is issued
first.

Issuing first in first out is the whole trick and it is easy to get wrong,
because the natural way to store boxes is to put the new ones on top. So the
issue order here is by expiry rather than by receipt, which is nearly the same
thing and differs exactly when a supplier ships something short dated.

Quantities are in sheets rather than in boxes. A box is a different number of
sheets depending on the size, and a crew that asks for two boxes and gets one
hundred sheets of the wrong size has still run out.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from qu_imager.errors import ConflictError, ValidationError

SHORT_DATED_DAYS = 60
"""Inside this a box is issued first, whatever else is on the shelf."""


@dataclass(slots=True)
class Lot:
    """One batch of film as it came from the supplier."""

    lot_number: str
    film_key: str
    size: str
    sheets: int
    received_on: date
    expires_on: date

    def __post_init__(self) -> None:
        if not self.lot_number.strip():
            raise ValidationError("a lot has a number")
        if self.sheets < 0:
            raise ValidationError("a lot cannot hold a negative number of sheets")
        if self.expires_on <= self.received_on:
            raise ValidationError("a lot expires after it arrived", lot=self.lot_number)

    def is_expired_on(self, day: date) -> bool:
        return day >= self.expires_on

    def is_short_dated_on(self, day: date) -> bool:
        return not self.is_expired_on(day) and (self.expires_on - day).days <= SHORT_DATED_DAYS

    def days_left_on(self, day: date) -> int:
        return (self.expires_on - day).days

    def describe(self) -> str:
        return f"{self.lot_number} {self.film_key} {self.size}, {self.sheets} sheets"


@dataclass(slots=True)
class Store:
    """What is on the shelf, and the order it comes off."""

    lots: list[Lot] = field(default_factory=list)

    def receive(self, lot: Lot) -> None:
        if any(existing.lot_number == lot.lot_number for existing in self.lots):
            raise ConflictError("that lot is already on the shelf", lot=lot.lot_number)
        self.lots.append(lot)

    def usable_on(self, day: date, film_key: str, size: str) -> tuple[Lot, ...]:
        """Everything that can be issued, shortest dated first."""
        fits = [
            lot
            for lot in self.lots
            if lot.film_key == film_key
            and lot.size == size
            and lot.sheets > 0
            and not lot.is_expired_on(day)
        ]
        return tuple(sorted(fits, key=lambda lot: (lot.expires_on, lot.lot_number)))

    def on_hand(self, day: date, film_key: str, size: str) -> int:
        return sum(lot.sheets for lot in self.usable_on(day, film_key, size))

    def issue(
        self, day: date, film_key: str, size: str, sheets: int
    ) -> tuple[tuple[str, int], ...]:
        """Take sheets off the shelf, shortest dated first, and say from where."""
        if sheets <= 0:
            raise ValidationError("an issue is at least one sheet", sheets=sheets)
        available = self.usable_on(day, film_key, size)
        if sum(lot.sheets for lot in available) < sheets:
            raise ConflictError(
                "there is not enough film on the shelf",
                film=film_key,
                size=size,
                wanted=sheets,
                on_hand=sum(lot.sheets for lot in available),
            )
        taken: list[tuple[str, int]] = []
        remaining = sheets
        for lot in available:
            if remaining == 0:
                break
            take = min(lot.sheets, remaining)
            lot.sheets -= take
            remaining -= take
            taken.append((lot.lot_number, take))
        return tuple(taken)

    def expired_on(self, day: date) -> tuple[str, ...]:
        return tuple(
            sorted(lot.lot_number for lot in self.lots if lot.is_expired_on(day) and lot.sheets > 0)
        )

    def short_dated_on(self, day: date) -> tuple[str, ...]:
        return tuple(
            sorted(
                lot.lot_number for lot in self.lots if lot.is_short_dated_on(day) and lot.sheets > 0
            )
        )

    def reorder_at(self, day: date, film_key: str, size: str, floor: int) -> bool:
        if floor < 0:
            raise ValidationError("a reorder floor is not negative", floor=floor)
        return self.on_hand(day, film_key, size) <= floor


def shelf_life(received_on: date, months: int = 18) -> date:
    """The usual dating, for a lot that arrives without a date on the box."""
    if months <= 0:
        raise ValidationError("a shelf life is a positive number of months")
    return received_on + timedelta(days=months * 30)
