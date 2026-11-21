package io.github.rkaushikdev.swagebay.persistence;

import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/** The two ways a repository reads at most one row. */
final class Queries {

    private Queries() {}

    /**
     * The one row the query finds, or nothing.
     *
     * @throws NonUniqueResultException when it finds more than one, which a unique key should have
     *     made impossible
     */
    static <T> Optional<T> oneOrNone(TypedQuery<T> query) {
        List<T> found = query.setMaxResults(2).getResultList();
        if (found.size() > 1) {
            throw new NonUniqueResultException("more than one row where at most one was expected");
        }
        return found.stream().findFirst();
    }

    /** The first row in the query's order, or nothing. */
    static <T> Optional<T> first(TypedQuery<T> query) {
        return query.setMaxResults(1).getResultStream().findFirst();
    }
}
