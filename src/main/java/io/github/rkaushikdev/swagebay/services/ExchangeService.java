package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.safety.DeviceDay;
import io.github.rkaushikdev.swagebay.safety.DeviceUnit;
import io.github.rkaushikdev.swagebay.safety.Trip;
import io.github.rkaushikdev.swagebay.safety.Use;
import io.github.rkaushikdev.swagebay.safety.UtilisationLog;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Exchange;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Recording an exchange, with the rules actually running.
 *
 * <p>The repository writes a row. That is all it should do, and it is not enough on its own,
 * because every rule about what may go into a device lives on the domain {@link
 * io.github.rkaushikdev.swagebay.sources.Loading} and a row written straight to the table skips all
 * of them.
 *
 * <p>So this reads the current loading back out of the exchange history, applies the new exchange
 * to it, and only writes the row if the domain accepted it. The replay is a handful of rows per
 * device and the alternative is a table that can say a device holds two sources.
 *
 * <p>The source states move here as well, because a source going out of a device is in transit and
 * one going in is in service, and leaving that to the caller means two callers doing it differently.
 */
public final class ExchangeService {

    private ExchangeService() {}

    /**
     * Apply the swap to the loading, then write it, or refuse and write nothing.
     *
     * @throws NotFoundError for a device, or a source either side of the swap, that is not on the
     *     inventory
     * @throws io.github.rkaushikdev.swagebay.SwagebayError whatever the loading refuses the swap for
     */
    public static void recordExchange(UnitOfWork work, Exchange exchange) {
        DeviceRow deviceRow = work.sources()
                .findDevice(exchange.deviceSerial())
                .orElseThrow(() -> new NotFoundError("device", exchange.deviceSerial()));
        DeviceUnit device = new DeviceUnit(deviceRow.getSerial(), Devices.deviceModel(deviceRow.getModelKey()));

        Optional<SourceRow> removedRow = exchange.removedSerial().map(work.sources()::get);
        Optional<SourceRow> installedRow = exchange.installedSerial().map(work.sources()::get);
        Optional<SealedSource> removed = removedRow.map(SourceMapper::rowToSource);
        Optional<SealedSource> installed = installedRow.map(SourceMapper::rowToSource);

        work.shop().loading().apply(exchange, device, removed, installed);

        removedRow.ifPresent(row -> row.setState(removed.orElseThrow().state().value()));
        installedRow.ifPresent(
                row -> row.setState(installed.orElseThrow().state().value()));
        work.shop().recordExchange(exchange);
        work.flush();
    }

    /** The source a device holds now, replayed from the exchanges, if it holds one. */
    public static Optional<String> sourceIn(UnitOfWork work, String deviceSerial) {
        return work.shop().loading().sourceIn(deviceSerial);
    }

    /** The device holding a source now, replayed from the exchanges, if one does. */
    public static Optional<String> deviceHolding(UnitOfWork work, String sourceSerial) {
        return work.shop().loading().deviceHolding(sourceSerial);
    }

    /**
     * Log a day's use, refusing one that contradicts the loading record.
     *
     * @throws io.github.rkaushikdev.swagebay.ConflictError when the loading record has the device
     *     holding a different source
     */
    public static void recordUse(UnitOfWork work, Use use) {
        UtilisationLog log = work.shop().logBetween(use.usedOn(), use.usedOn());
        log.record(use, work.shop().loading());
        work.shop().recordUse(use);
        work.flush();
    }

    /** Every day from {@code since} to {@code until} a device was out on a trip with nothing in the utilisation log. */
    public static List<DeviceDay> unloggedDays(UnitOfWork work, LocalDate since, LocalDate until) {
        List<Trip> trips = work.sources().tripsBetween(since, until);
        return work.shop().logBetween(since, until).unloggedDays(trips);
    }
}
