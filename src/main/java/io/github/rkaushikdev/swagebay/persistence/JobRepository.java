package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Jobs, and the welds under them. */
public final class JobRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public JobRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The job with that number, if there is one. */
    public Optional<JobRow> find(JobNumber number) {
        return Queries.oneOrNone(entityManager
                .createQuery("select j from JobRow j where j.year = :year and j.serial = :serial", JobRow.class)
                .setParameter("year", number.year())
                .setParameter("serial", number.serial()));
    }

    /**
     * The job with that number.
     *
     * @throws NotFoundError when there is none, with the number as it is written
     */
    public JobRow get(JobNumber number) {
        return find(number).orElseThrow(() -> new NotFoundError("job", number.toString()));
    }

    /** The jobs open on {@code day}: opened on or before it and not closed before it, in number order. */
    public List<JobRow> openOn(LocalDate day) {
        return entityManager
                .createQuery(
                        "select j from JobRow j where j.openedOn <= :day"
                                + " and (j.closedOn is null or j.closedOn >= :day) order by j.year, j.serial",
                        JobRow.class)
                .setParameter("day", day)
                .getResultList();
    }

    /** The serial after the highest one used in {@code year}, or one in a year nothing used yet. */
    public int nextSerial(int year) {
        return Queries.first(entityManager
                        .createQuery(
                                "select j.serial from JobRow j where j.year = :year order by j.serial desc",
                                Integer.class)
                        .setParameter("year", year))
                .map(used -> used + 1)
                .orElse(1);
    }

    /** Write a new job, and whatever hangs off it, in this unit's transaction. */
    public void add(JobRow job) {
        entityManager.persist(job);
    }
}
