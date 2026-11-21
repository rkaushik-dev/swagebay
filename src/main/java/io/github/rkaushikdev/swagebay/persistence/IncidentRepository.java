package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Abnormal occurrences and what is owed because of them.
 *
 * <p>Each of the small registers answers the same question in its own words: what is outstanding
 * today. This one asks what has not been reported yet. They are written out separately rather than
 * through one generic overdue query, because the date each one compares against is a different
 * column with a different meaning, and a generic query would need the column name as an argument,
 * which is a query with a hole in it.
 */
public final class IncidentRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public IncidentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The incident with that reference, if there is one. */
    public Optional<IncidentRow> find(String reference) {
        return Queries.oneOrNone(entityManager
                .createQuery("select i from IncidentRow i where i.reference = :reference", IncidentRow.class)
                .setParameter("reference", reference));
    }

    /**
     * The incident with that reference.
     *
     * @throws NotFoundError when there is none
     */
    public IncidentRow get(String reference) {
        return find(reference).orElseThrow(() -> new NotFoundError("incident", reference));
    }

    /** Every incident nobody has notified yet, oldest found first. */
    public List<IncidentRow> unreported() {
        return entityManager
                .createQuery(
                        "select i from IncidentRow i where i.notifiedAt is null order by i.discoveredAt, i.reference",
                        IncidentRow.class)
                .getResultList();
    }

    /** Every incident found at or after {@code moment}, oldest first. */
    public List<IncidentRow> since(LocalDateTime moment) {
        return entityManager
                .createQuery(
                        "select i from IncidentRow i where i.discoveredAt >= :moment order by i.discoveredAt",
                        IncidentRow.class)
                .setParameter("moment", moment)
                .getResultList();
    }
}
