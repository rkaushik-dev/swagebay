package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;

/**
 * A unit of work on a test database, used the way the table tests use one: add rows, commit, and
 * read them back.
 *
 * <p>A commit here also forgets everything the entity manager had loaded, so every read after it
 * comes from the database rather than from the rows still held in memory. That is what makes a
 * round trip a round trip. Closing without committing rolls back, as a unit of work does.
 */
final class TestSession implements AutoCloseable {

    private final UnitOfWork work;

    private TestSession(UnitOfWork work) {
        this.work = work;
    }

    /** A session on a database of its own, with the schema in it and nothing else. */
    static TestSession open() {
        return on(TestDatabase.fresh());
    }

    /** A session on {@code database}, which other sessions may share. */
    static TestSession on(Database database) {
        return new TestSession(database.unitOfWork());
    }

    EntityManager entityManager() {
        return work.entityManager();
    }

    /** Write {@code row}, and whatever hangs off it, inside the transaction. */
    <T> T add(T row) {
        work.entityManager().persist(row);
        return row;
    }

    /** Write each of {@code rows}, in order. */
    void addAll(Object... rows) {
        for (Object row : rows) {
            add(row);
        }
    }

    void flush() {
        work.flush();
    }

    /** Commit, then forget what was loaded so the next read goes to the database. */
    void commit() {
        work.commit();
        work.entityManager().clear();
    }

    void rollback() {
        work.rollback();
    }

    /** Delete {@code row}, and whatever hangs off it, whether or not it is still loaded. */
    void delete(Keyed row) {
        EntityManager entityManager = work.entityManager();
        entityManager.remove(entityManager.contains(row) ? row : reread(row));
    }

    /** {@code row} as the database has it now. */
    <T extends Keyed> T reread(T row) {
        @SuppressWarnings("unchecked") // the class of a row is the class of its type
        Class<T> type = (Class<T>) row.getClass();
        return work.entityManager().find(type, row.getId().orElseThrow());
    }

    /** Every row of {@code type}, in the order they were written. */
    <T> List<T> all(Class<T> type) {
        return select(type).getResultList();
    }

    /** The one row of {@code type}; none, or more than one, is an error. */
    <T> T one(Class<T> type) {
        return select(type).getSingleResult();
    }

    private <T> TypedQuery<T> select(Class<T> type) {
        CriteriaBuilder builder = work.entityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(type);
        Root<T> root = query.from(type);
        query.select(root).orderBy(builder.asc(root.get("id")));
        return work.entityManager().createQuery(query);
    }

    @Override
    public void close() {
        work.close();
    }
}
