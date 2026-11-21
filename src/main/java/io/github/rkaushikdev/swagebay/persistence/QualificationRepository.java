package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.QualificationRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** Welder qualifications and their continuity. */
public final class QualificationRepository implements Repository {

    /** How many days a welder may go without using a process before the qualification lapses. */
    public static final int CONTINUITY_DAYS = 180;

    private final EntityManager entityManager;

    /**
     * A welder who has gone quiet on a process.
     *
     * @param welder the welder's stamp
     * @param process the process they have not used
     */
    public record Lapse(String welder, String process) {}

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public QualificationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** Every qualification one welder holds, oldest first. */
    public List<QualificationRow> forWelder(String welder) {
        return entityManager
                .createQuery(
                        "select q from QualificationRow q where q.welder = :welder order by q.qualifiedOn",
                        QualificationRow.class)
                .setParameter("welder", welder)
                .getResultList();
    }

    /** {@link #lapsedOn(LocalDate, int)} over the usual {@link #CONTINUITY_DAYS}. */
    public List<Lapse> lapsedOn(LocalDate day) {
        return lapsedOn(day, CONTINUITY_DAYS);
    }

    /**
     * Each welder and process, once, sorted, last used {@code continuityDays} or more before {@code
     * day}.
     *
     * @throws ValidationError for a window shorter than one day
     */
    public List<Lapse> lapsedOn(LocalDate day, int continuityDays) {
        if (continuityDays < 1) {
            throw new ValidationError("continuity is a positive number of days");
        }
        LocalDate cutoff = day.minusDays(continuityDays);
        return entityManager
                .createQuery(
                        "select q from QualificationRow q where q.lastUsedOn <= :cutoff order by q.welder, q.process",
                        QualificationRow.class)
                .setParameter("cutoff", cutoff)
                .getResultList()
                .stream()
                .map(row -> new Lapse(row.getWelder(), row.getProcess()))
                .distinct()
                .toList();
    }
}
