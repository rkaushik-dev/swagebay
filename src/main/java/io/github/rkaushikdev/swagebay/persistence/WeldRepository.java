package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/** Welds and their radiographs. */
public final class WeldRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public WeldRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The weld on {@code job} with that identifier and repair round, if there is one. */
    public Optional<WeldRow> find(JobRow job, WeldId weld) {
        return Queries.oneOrNone(entityManager
                .createQuery(
                        "select w from WeldRow w where w.job = :job and w.prefix = :prefix"
                                + " and w.number = :number and w.repairRound = :round",
                        WeldRow.class)
                .setParameter("job", job)
                .setParameter("prefix", weld.prefix())
                .setParameter("number", weld.number())
                .setParameter("round", weld.repair()));
    }

    /**
     * The weld on {@code job} with that identifier and repair round.
     *
     * @throws NotFoundError when there is none, with the identifier as it is written
     */
    public WeldRow get(JobRow job, WeldId weld) {
        return find(job, weld).orElseThrow(() -> new NotFoundError("weld", weld.toString()));
    }

    /** Every round of one weld on {@code job}, the weld as first made first. */
    public List<WeldRow> rounds(JobRow job, WeldId weld) {
        return entityManager
                .createQuery(
                        "select w from WeldRow w where w.job = :job and w.prefix = :prefix"
                                + " and w.number = :number order by w.repairRound",
                        WeldRow.class)
                .setParameter("job", job)
                .setParameter("prefix", weld.prefix())
                .setParameter("number", weld.number())
                .getResultList();
    }

    /**
     * The radiograph with that number, which is unique across the shop.
     *
     * @throws NotFoundError when there is none
     */
    public RadiographRow radiograph(String number) {
        return Queries.oneOrNone(entityManager
                        .createQuery("select r from RadiographRow r where r.number = :number", RadiographRow.class)
                        .setParameter("number", number))
                .orElseThrow(() -> new NotFoundError("radiograph", number));
    }

    /** The radiographs on {@code job} nobody has read yet, by number. */
    public List<RadiographRow> unread(JobRow job) {
        return entityManager
                .createQuery(
                        "select r from RadiographRow r join r.weld w where w.job = :job and r.readAt is null"
                                + " order by r.number",
                        RadiographRow.class)
                .setParameter("job", job)
                .getResultList();
    }

    /**
     * One page of the radiographs on {@code job}, by number, starting after the number the client
     * saw last, or from the start without one.
     *
     * <p>The number is unique across the shop, so it is a sound cursor on its own and no tie break
     * column is needed. That is the only reason the cursor can be the number rather than an opaque
     * token.
     *
     * @throws ValidationError for a page of fewer than one row
     */
    public List<RadiographRow> radiographsAfter(JobRow job, Optional<String> cursor, int limit) {
        if (limit < 1) {
            throw new ValidationError("a page holds at least one row", Context.of("limit", limit));
        }
        String after = cursor.isPresent() ? " and r.number > :cursor" : "";
        TypedQuery<RadiographRow> query = entityManager
                .createQuery(
                        "select r from RadiographRow r join r.weld w where w.job = :job" + after + " order by r.number",
                        RadiographRow.class)
                .setParameter("job", job)
                .setMaxResults(limit);
        cursor.ifPresent(number -> query.setParameter("cursor", number));
        return query.getResultList();
    }

    /** The radiographs on {@code job} that are not admissible, by number. */
    public List<RadiographRow> inadmissible(JobRow job) {
        return entityManager
                .createQuery(
                        "select r from RadiographRow r join r.weld w where w.job = :job and r.admissible = false"
                                + " order by r.number",
                        RadiographRow.class)
                .setParameter("job", job)
                .getResultList();
    }
}
