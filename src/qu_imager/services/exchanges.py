"""Recording an exchange, with the rules actually running.

The repository writes a row. That is all it should do, and it is not enough on
its own, because every rule about what may go into a device lives on the domain
Loading object and a row written straight to the table skips all of them.

So this reads the current loading back out of the exchange history, applies the
new exchange to it, and only writes the row if the domain accepted it. The
replay is a handful of rows per device and the alternative is a table that can
say a device holds two sources.

The source states move here as well, because a source going out of a device is
in transit and one going in is in service, and leaving that to the caller means
two callers doing it differently.
"""

from __future__ import annotations

from datetime import date

from qu_imager.errors import NotFoundError
from qu_imager.persistence.source_mapper import row_to_source
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.safety.inventory import DeviceUnit
from qu_imager.safety.utilisation import Use
from qu_imager.sources.device import device_model
from qu_imager.sources.exchange import Exchange


def record_exchange(work: UnitOfWork, exchange: Exchange) -> None:
    """Apply the swap to the loading, then write it, or refuse and write nothing."""
    device_row = work.sources.find_device(exchange.device_serial)
    if device_row is None:
        raise NotFoundError("device", exchange.device_serial)
    device = DeviceUnit(serial=device_row.serial, model=device_model(device_row.model_key))

    removed_row = work.sources.get(exchange.removed_serial) if exchange.removed_serial else None
    installed_row = (
        work.sources.get(exchange.installed_serial) if exchange.installed_serial else None
    )
    removed = row_to_source(removed_row) if removed_row is not None else None
    installed = row_to_source(installed_row) if installed_row is not None else None

    loading = work.shop.loading()
    loading.apply(exchange, device, removed, installed)

    if removed_row is not None and removed is not None:
        removed_row.state = removed.state.value
    if installed_row is not None and installed is not None:
        installed_row.state = installed.state.value
    work.shop.record_exchange(exchange)
    work.flush()


def source_in(work: UnitOfWork, device_serial: str) -> str | None:
    return work.shop.loading().source_in(device_serial)


def device_holding(work: UnitOfWork, source_serial: str) -> str | None:
    return work.shop.loading().device_holding(source_serial)


def record_use(work: UnitOfWork, use: Use) -> None:
    """Log a day's use, refusing one that contradicts the loading record."""
    log = work.shop.log_between(use.used_on, use.used_on)
    log.record(use, work.shop.loading())
    work.shop.record_use(use)
    work.flush()


def unlogged_days(work: UnitOfWork, since: date, until: date) -> tuple[tuple[str, date], ...]:
    """Days a device was out on a trip with nothing in the utilisation log."""
    trips = work.sources.trips_between(since, until)
    return work.shop.log_between(since, until).unlogged_days(list(trips))
