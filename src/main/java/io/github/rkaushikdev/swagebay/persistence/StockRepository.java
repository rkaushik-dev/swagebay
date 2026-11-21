package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.persistence.models.LotRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** Film on the shelf, and which of it can still be used. */
public final class StockRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public StockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /**
     * The lots of one film in one size with sheets left that are still in date on {@code day},
     * shortest dated first, which is the order they come off the shelf.
     */
    public List<LotRow> usableOn(LocalDate day, String filmKey, String size) {
        return entityManager
                .createQuery(
                        "select l from LotRow l where l.filmKey = :film and l.size = :size and l.sheets > 0"
                                + " and l.expiresOn > :day order by l.expiresOn, l.lotNumber",
                        LotRow.class)
                .setParameter("film", filmKey)
                .setParameter("size", size)
                .setParameter("day", day)
                .getResultList();
    }

    /** How many usable sheets of one film in one size there are on {@code day}. */
    public int onHand(LocalDate day, String filmKey, String size) {
        return usableOn(day, filmKey, size).stream().mapToInt(LotRow::getSheets).sum();
    }

    /** The lot numbers, sorted, of every lot with film left in it that is out of date by {@code day}. */
    public List<String> expiredOn(LocalDate day) {
        return entityManager
                .createQuery(
                        "select l.lotNumber from LotRow l where l.expiresOn <= :day and l.sheets > 0"
                                + " order by l.lotNumber",
                        String.class)
                .setParameter("day", day)
                .getResultList();
    }
}
