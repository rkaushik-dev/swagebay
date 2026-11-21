package io.github.rkaushikdev.swagebay.persistence;

import jakarta.persistence.EntityManager;

/**
 * Anything a unit of work builds on its entity manager when it opens: every repository, and the
 * counters. They all share that one entity manager, which is what makes a piece of work written
 * through several of them one transaction.
 */
public interface Repository {

    /** The entity manager this reads and writes through. */
    EntityManager entityManager();
}
