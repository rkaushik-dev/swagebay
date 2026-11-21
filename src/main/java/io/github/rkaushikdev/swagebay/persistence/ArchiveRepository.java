package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.persistence.models.FiledRow;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/** The filing cabinet: which radiograph is filed where. */
public final class ArchiveRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public ArchiveRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** Where the radiograph with that number is filed, if it has been. */
    public Optional<FiledRow> find(String radiograph) {
        return Queries.oneOrNone(entityManager
                .createQuery("select f from FiledRow f where f.radiograph = :radiograph", FiledRow.class)
                .setParameter("radiograph", radiograph));
    }

    /** Every radiograph filed against one job, by number. */
    public List<FiledRow> forJob(String job) {
        return entityManager
                .createQuery("select f from FiledRow f where f.job = :job order by f.radiograph", FiledRow.class)
                .setParameter("job", job)
                .getResultList();
    }

    /** The numbers, sorted, of every filed radiograph that has not been put in a box yet. */
    public List<String> withoutALocation() {
        return entityManager
                .createQuery(
                        "select f.radiograph from FiledRow f where f.box is null order by f.radiograph", String.class)
                .getResultList();
    }
}
