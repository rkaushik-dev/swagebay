"""Reading and writing the two shop registers.

The loading state is derived from the exchange rows rather than stored beside
them. Which source is in which device is a fact about the last exchange, and
keeping a second copy of it in a column is keeping a fact in two places, which
is how a device ends up holding one source on the inventory screen and another
one on the exchange history.

That means every question about the loading replays the exchanges for the
devices asked about. There are two of those a year per device, so the replay is
a handful of rows and the alternative is a reconciliation job.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import ValidationError
from qu_imager.persistence.models.shop import ExchangeRow, UseRow
from qu_imager.safety.utilisation import Use, UtilisationLog
from qu_imager.sources.exchange import Exchange, Loading

SEPARATOR = ","


def row_to_exchange(row: ExchangeRow) -> Exchange:
    return Exchange(
        device_serial=row.device_serial,
        removed_serial=row.removed_serial,
        installed_serial=row.installed_serial,
        performed_on=row.performed_on,
        performed_by=row.performed_by,
        shipment=row.shipment,
        notes=row.notes,
    )


def row_to_use(row: UseRow) -> Use:
    return Use(
        used_on=row.used_on,
        device_serial=row.device_serial,
        source_serial=row.source_serial,
        site=row.site,
        radiographers=tuple(row.radiographers.split(SEPARATOR)),
        exposures=row.exposures,
    )


class ShopRepository:
    """Exchanges and uses, and the two domain objects built from them."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def record_exchange(self, exchange: Exchange) -> ExchangeRow:
        row = ExchangeRow(
            device_serial=exchange.device_serial,
            removed_serial=exchange.removed_serial,
            installed_serial=exchange.installed_serial,
            performed_on=exchange.performed_on,
            performed_by=exchange.performed_by,
            shipment=exchange.shipment,
            notes=exchange.notes,
        )
        self.session.add(row)
        return row

    def exchanges_for(self, device_serial: str) -> tuple[Exchange, ...]:
        rows = self.session.scalars(
            select(ExchangeRow)
            .where(ExchangeRow.device_serial == device_serial)
            .order_by(ExchangeRow.performed_on, ExchangeRow.id)
        ).all()
        return tuple(row_to_exchange(row) for row in rows)

    def exchanges_on(self, shipment: str) -> tuple[Exchange, ...]:
        rows = self.session.scalars(
            select(ExchangeRow)
            .where(ExchangeRow.shipment == shipment)
            .order_by(ExchangeRow.performed_on, ExchangeRow.id)
        ).all()
        return tuple(row_to_exchange(row) for row in rows)

    def loading(self) -> Loading:
        """Which source is in which device, replayed from the exchanges."""
        rows = self.session.scalars(
            select(ExchangeRow).order_by(ExchangeRow.performed_on, ExchangeRow.id)
        ).all()
        held = Loading()
        for row in rows:
            exchange = row_to_exchange(row)
            if exchange.removed_serial is not None:
                held.held.pop(exchange.device_serial, None)
            if exchange.installed_serial is not None:
                held.held[exchange.device_serial] = exchange.installed_serial
            held.history.append(exchange)
        return held

    def record_use(self, use: Use) -> UseRow:
        row = UseRow(
            device_serial=use.device_serial,
            source_serial=use.source_serial,
            used_on=use.used_on,
            site=use.site,
            radiographers=SEPARATOR.join(use.radiographers),
            exposures=use.exposures,
        )
        self.session.add(row)
        return row

    def uses_between(self, since: date, until: date) -> tuple[Use, ...]:
        rows = self.session.scalars(
            select(UseRow)
            .where(UseRow.used_on >= since, UseRow.used_on <= until)
            .order_by(UseRow.used_on, UseRow.device_serial, UseRow.id)
        ).all()
        return tuple(row_to_use(row) for row in rows)

    def log_between(self, since: date, until: date) -> UtilisationLog:
        log = UtilisationLog()
        for use in self.uses_between(since, until):
            log.uses.append(use)
        return log

    def use_on(self, device_serial: str, day: date, site: str) -> Use | None:
        row = self.session.scalars(
            select(UseRow).where(
                UseRow.device_serial == device_serial,
                UseRow.used_on == day,
                UseRow.site == site,
            )
        ).first()
        return row_to_use(row) if row is not None else None

    def add_exposures(self, device_serial: str, day: date, site: str, exposures: int) -> Use:
        """Top up a count written before the films were counted."""
        if exposures < 0:
            raise ValidationError("exposures do not go backwards", value=str(exposures))
        row = self.session.scalars(
            select(UseRow).where(
                UseRow.device_serial == device_serial,
                UseRow.used_on == day,
                UseRow.site == site,
            )
        ).first()
        if row is None:
            raise ValidationError(
                "nothing was logged for that device that day", device=device_serial
            )
        row.exposures += exposures
        return row_to_use(row)
