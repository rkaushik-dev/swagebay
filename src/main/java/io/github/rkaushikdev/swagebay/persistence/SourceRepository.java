package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.safety.Trip;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Sealed sources and the devices they ride in. */
public final class SourceRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public SourceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The source with that serial, if there is one. */
    public Optional<SourceRow> find(String serial) {
        return Queries.oneOrNone(entityManager
                .createQuery("select s from SourceRow s where s.serial = :serial", SourceRow.class)
                .setParameter("serial", serial));
    }

    /**
     * The source with that serial.
     *
     * @throws NotFoundError when there is none
     */
    public SourceRow get(String serial) {
        return find(serial).orElseThrow(() -> new NotFoundError("source", serial));
    }

    /**
     * Every source that can go out, sorted by serial, with its wipe tests already loaded.
     *
     * <p>Whether a source is fit is a question about its last wipe test, so every caller of this
     * walks the wipes. Leaving them to be loaded one source at a time made the morning sheet read
     * once per source, and the count grew with the shop.
     */
    public List<SourceRow> inService() {
        return entityManager
                .createQuery(
                        "select s from SourceRow s left join fetch s.leakTests"
                                + " where s.state = 'in service' order by s.serial",
                        SourceRow.class)
                .getResultList();
    }

    /**
     * The trip the source is out on, if it is out.
     *
     * @throws NotFoundError when there is no such source
     */
    public Optional<TripRow> openTrip(String serial) {
        SourceRow source = get(serial);
        return Queries.oneOrNone(entityManager
                .createQuery("select t from TripRow t where t.source = :source and t.cameBack is null", TripRow.class)
                .setParameter("source", source));
    }

    /**
     * Every trip that overlapped the days from {@code since} to {@code until}, both included, as the
     * domain has them, in the order they went out.
     *
     * <p>The overlap test is deliberately not an equality on the departure date. A trip that went
     * out in June and came back in July is a July trip as far as the utilisation log is concerned,
     * and a query keyed on the departure alone silently drops it.
     */
    public List<Trip> tripsBetween(LocalDate since, LocalDate until) {
        List<Tuple> rows = entityManager
                .createQuery(
                        "select s.serial, d.serial, t.crewBadge, t.site, t.wentOut, t.cameBack, t.exposures"
                                + " from TripRow t join t.source s join t.device d"
                                + " where t.wentOut <= :until and (t.cameBack is null or t.cameBack >= :since)"
                                + " order by t.wentOut, t.id",
                        Tuple.class)
                .setParameter("since", since)
                .setParameter("until", until)
                .getResultList();
        return rows.stream()
                .map(row -> new Trip(
                        row.get(0, String.class),
                        row.get(1, String.class),
                        row.get(2, String.class),
                        row.get(3, String.class),
                        row.get(4, LocalDate.class),
                        Optional.ofNullable(row.get(5, LocalDate.class)),
                        row.get(6, Integer.class)))
                .toList();
    }

    /** The serial of every source that is out, sorted, in one query rather than one each. */
    public Set<String> openTripSerials() {
        List<String> serials = entityManager
                .createQuery("select s.serial from TripRow t join t.source s where t.cameBack is null", String.class)
                .getResultList();
        return Collections.unmodifiableSet(new TreeSet<>(serials));
    }

    /** The device with that serial, if there is one. */
    public Optional<DeviceRow> findDevice(String serial) {
        return Queries.oneOrNone(entityManager
                .createQuery("select d from DeviceRow d where d.serial = :serial", DeviceRow.class)
                .setParameter("serial", serial));
    }

    /**
     * The device with that serial.
     *
     * @throws NotFoundError when there is none
     */
    public DeviceRow device(String serial) {
        return findDevice(serial).orElseThrow(() -> new NotFoundError("device", serial));
    }

    /** Every device, sorted by serial. */
    public List<DeviceRow> devices() {
        return entityManager
                .createQuery("select d from DeviceRow d order by d.serial", DeviceRow.class)
                .getResultList();
    }

    /** Write a new source, and whatever hangs off it, in this unit's transaction. */
    public void add(SourceRow source) {
        entityManager.persist(source);
    }
}
