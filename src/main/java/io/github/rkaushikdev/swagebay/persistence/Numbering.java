package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.CounterRow;
import io.github.rkaushikdev.swagebay.util.Text;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gapless numbers, read and advanced inside somebody else's transaction, and formatted the way the
 * paperwork writes them.
 *
 * <p>A number is taken by advancing a counter row in the caller's transaction, so if the work it
 * numbers rolls back, the number rolls back with it and is issued again. See {@link CounterRow}.
 *
 * @param entityManager the entity manager of the unit of work the numbers are taken in
 */
public record Numbering(EntityManager entityManager) implements Repository {

    /** The counter radiograph numbers are taken from, one per job. */
    public static final String RADIOGRAPH = "radiograph";

    /** The counter report numbers are taken from, one per job. */
    public static final String REPORT = "report";

    /** {@link #take(String, String)} in the scope with no name. */
    public int take(String name) {
        return take(name, "");
    }

    /**
     * The next number of counter {@code name} in {@code scope}, advanced in this transaction and no
     * other. The counter row is locked until the transaction ends, so two units of work cannot be
     * handed the same number.
     *
     * @throws ValidationError when the counter has no name
     */
    public int take(String name, String scope) {
        if (Text.strip(name).isEmpty()) {
            throw new ValidationError("a counter has a name");
        }
        CounterRow row = counter(name, scope, LockModeType.PESSIMISTIC_WRITE).orElseGet(() -> {
            CounterRow created = new CounterRow();
            created.setName(name);
            created.setScope(scope);
            created.setNextValue(1);
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
        int issued = row.getNextValue();
        row.setNextValue(issued + 1);
        entityManager.flush();
        return issued;
    }

    /** {@link #peek(String, String)} in the scope with no name. */
    public int peek(String name) {
        return peek(name, "");
    }

    /** What {@link #take(String, String)} would issue next, without taking it. */
    public int peek(String name, String scope) {
        return counter(name, scope, LockModeType.NONE)
                .map(CounterRow::getNextValue)
                .orElse(1);
    }

    /** The next radiograph number of {@code job}, such as {@code 24-0417-R0001}. */
    public String takeRadiographNumber(String job) {
        return formatRadiograph(job, take(RADIOGRAPH, job));
    }

    /** The next report number of {@code job}, such as {@code 24-0417-RPT001}. */
    public String takeReportNumber(String job) {
        return formatReport(job, take(REPORT, job));
    }

    /**
     * A radiograph number the way the envelope is written: the job, {@code R}, and the number to at
     * least four digits.
     *
     * @throws ValidationError when {@code issued} is below one
     */
    public static String formatRadiograph(String job, int issued) {
        if (issued < 1) {
            throw new ValidationError("a radiograph number starts at one", Context.of("issued", issued));
        }
        return String.format(Locale.ROOT, "%s-R%04d", job, issued);
    }

    /**
     * A report number: the job, {@code RPT}, and the number to at least three digits.
     *
     * @throws ValidationError when {@code issued} is below one
     */
    public static String formatReport(String job, int issued) {
        if (issued < 1) {
            throw new ValidationError("a report number starts at one", Context.of("issued", issued));
        }
        return String.format(Locale.ROOT, "%s-RPT%03d", job, issued);
    }

    private Optional<CounterRow> counter(String name, String scope, LockModeType lock) {
        TypedQuery<CounterRow> query = entityManager
                .createQuery("select c from CounterRow c where c.name = :name and c.scope = :scope", CounterRow.class)
                .setParameter("name", name)
                .setParameter("scope", scope)
                .setLockMode(lock);
        List<CounterRow> found = query.getResultList();
        return found.stream().findFirst();
    }
}
