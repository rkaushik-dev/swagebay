package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.CalibrationRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Instruments and their certificates, and which of them have run out. */
public final class CalibrationRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public CalibrationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The most recent certificate of the instrument with that serial, the one that counts. */
    public Optional<CalibrationRow> latestFor(String serial) {
        return Queries.first(entityManager
                .createQuery(
                        "select c from CalibrationRow c where c.serial = :serial order by c.calibratedOn desc",
                        CalibrationRow.class)
                .setParameter("serial", serial));
    }

    /** The serials, sorted, of every instrument whose latest certificate has run out by {@code day}. */
    public List<String> expiredOn(LocalDate day) {
        List<String> expired = new ArrayList<>();
        for (String serial : serials()) {
            Optional<CalibrationRow> latest = latestFor(serial);
            if (latest.isEmpty() || !latest.get().getDueOn().isAfter(day)) {
                expired.add(serial);
            }
        }
        return List.copyOf(expired);
    }

    /**
     * The serials, sorted, of every instrument still in date on {@code day} whose latest certificate
     * runs out within {@code days} of it, which is what can still be booked in.
     *
     * @throws ValidationError for a look ahead below zero
     */
    public List<String> dueWithin(LocalDate day, int days) {
        if (days < 0) {
            throw new ValidationError("a look ahead is not negative", Context.of("days", days));
        }
        LocalDate horizon = day.plusDays(days);
        List<String> due = new ArrayList<>();
        for (String serial : serials()) {
            Optional<LocalDate> dueOn = latestFor(serial).map(CalibrationRow::getDueOn);
            if (dueOn.isPresent() && dueOn.get().isAfter(day) && !dueOn.get().isAfter(horizon)) {
                due.add(serial);
            }
        }
        return List.copyOf(due);
    }

    private List<String> serials() {
        return entityManager
                .createQuery("select distinct c.serial from CalibrationRow c order by c.serial", String.class)
                .getResultList();
    }
}
