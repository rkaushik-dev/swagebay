package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.ExchangeRow;
import io.github.rkaushikdev.swagebay.persistence.models.UseRow;
import io.github.rkaushikdev.swagebay.safety.Use;
import io.github.rkaushikdev.swagebay.safety.UtilisationLog;
import io.github.rkaushikdev.swagebay.sources.Exchange;
import io.github.rkaushikdev.swagebay.sources.Loading;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Source exchanges and device uses, the two shop registers, and the two domain objects built from
 * them.
 *
 * <p>Which source is in which device is derived from the exchange rows rather than stored beside
 * them. It is a fact about the last exchange, and keeping a second copy of it in a column is keeping
 * a fact in two places, which is how a device ends up holding one source on the inventory screen
 * and another one on the exchange history. So every question about the loading replays the
 * exchanges. There are two of those a year per device, so the replay is a handful of rows, and the
 * alternative is a reconciliation job.
 */
public final class ShopRepository implements Repository {

    /** What separates the badges in the column of radiographers on a use. */
    public static final String SEPARATOR = ",";

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public ShopRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** A row back into the exchange it records. */
    public static Exchange rowToExchange(ExchangeRow row) {
        return new Exchange(
                row.getDeviceSerial(),
                row.getRemovedSerial(),
                row.getInstalledSerial(),
                row.getPerformedOn(),
                row.getPerformedBy(),
                row.getShipment(),
                row.getNotes());
    }

    /** A row back into the use it records, its crew in the order they were written. */
    public static Use rowToUse(UseRow row) {
        return new Use(
                row.getUsedOn(),
                row.getDeviceSerial(),
                row.getSourceSerial(),
                row.getSite(),
                List.of(row.getRadiographers().split(Pattern.quote(SEPARATOR), -1)),
                row.getExposures());
    }

    /** Write an exchange in this unit's transaction. */
    public ExchangeRow recordExchange(Exchange exchange) {
        ExchangeRow row = new ExchangeRow();
        row.setDeviceSerial(exchange.deviceSerial());
        row.setRemovedSerial(exchange.removedSerial().orElse(null));
        row.setInstalledSerial(exchange.installedSerial().orElse(null));
        row.setPerformedOn(exchange.performedOn());
        row.setPerformedBy(exchange.performedBy());
        row.setShipment(exchange.shipment());
        row.setNotes(exchange.notes());
        entityManager.persist(row);
        return row;
    }

    /** Every exchange on one device, in the order they were made. */
    public List<Exchange> exchangesFor(String deviceSerial) {
        return exchanges("where e.deviceSerial = :value", deviceSerial);
    }

    /** Every exchange that came on one shipment, in the order they were made. */
    public List<Exchange> exchangesOn(String shipment) {
        return exchanges("where e.shipment = :value", shipment);
    }

    /** Which source is in which device, replayed from every exchange in the order they were made. */
    public Loading loading() {
        Loading held = new Loading();
        entityManager
                .createQuery("select e from ExchangeRow e order by e.performedOn, e.id", ExchangeRow.class)
                .getResultList()
                .forEach(row -> held.replay(rowToExchange(row)));
        return held;
    }

    /** Write a use in this unit's transaction. */
    public UseRow recordUse(Use use) {
        UseRow row = new UseRow();
        row.setDeviceSerial(use.deviceSerial());
        row.setSourceSerial(use.sourceSerial());
        row.setUsedOn(use.usedOn());
        row.setSite(use.site());
        row.setRadiographers(String.join(SEPARATOR, use.radiographers()));
        row.setExposures(use.exposures());
        entityManager.persist(row);
        return row;
    }

    /** Every use from {@code since} to {@code until}, both included, by day and then device. */
    public List<Use> usesBetween(LocalDate since, LocalDate until) {
        return entityManager
                .createQuery(
                        "select u from UseRow u where u.usedOn >= :since and u.usedOn <= :until"
                                + " order by u.usedOn, u.deviceSerial, u.id",
                        UseRow.class)
                .setParameter("since", since)
                .setParameter("until", until)
                .getResultList()
                .stream()
                .map(ShopRepository::rowToUse)
                .toList();
    }

    /** The utilisation log from {@code since} to {@code until}, both included. */
    public UtilisationLog logBetween(LocalDate since, LocalDate until) {
        UtilisationLog log = new UtilisationLog();
        usesBetween(since, until).forEach(log::record);
        return log;
    }

    /** The use of one device on one day at one site, if one was logged. */
    public Optional<Use> useOn(String deviceSerial, LocalDate day, String site) {
        return row(deviceSerial, day, site).map(ShopRepository::rowToUse);
    }

    /**
     * Top up the exposures of a use written before the films were counted.
     *
     * @throws ValidationError for a negative top up, or a day nothing was logged for that device at
     *     that site
     */
    public Use addExposures(String deviceSerial, LocalDate day, String site, int exposures) {
        if (exposures < 0) {
            throw new ValidationError("exposures do not go backwards", Context.of("value", String.valueOf(exposures)));
        }
        UseRow row = row(deviceSerial, day, site)
                .orElseThrow(() -> new ValidationError(
                        "nothing was logged for that device that day", Context.of("device", deviceSerial)));
        row.setExposures(row.getExposures() + exposures);
        return rowToUse(row);
    }

    private Optional<UseRow> row(String deviceSerial, LocalDate day, String site) {
        return Queries.first(entityManager
                .createQuery(
                        "select u from UseRow u where u.deviceSerial = :device and u.usedOn = :day and u.site = :site",
                        UseRow.class)
                .setParameter("device", deviceSerial)
                .setParameter("day", day)
                .setParameter("site", site));
    }

    private List<Exchange> exchanges(String where, String value) {
        return entityManager
                .createQuery(
                        "select e from ExchangeRow e " + where + " order by e.performedOn, e.id", ExchangeRow.class)
                .setParameter("value", value)
                .getResultList()
                .stream()
                .map(ShopRepository::rowToExchange)
                .toList();
    }
}
