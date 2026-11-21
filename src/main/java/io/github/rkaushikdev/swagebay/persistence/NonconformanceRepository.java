package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Register;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.models.NonconformanceRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Raising, deciding and closing non conformance reports, and the lists somebody asks for.
 *
 * <p>Deciding and closing go through the domain object rather than being written as column updates,
 * which is why both load the report, call it, and write it back. Setting a disposition with an update
 * statement would skip the rule that use as is needs a concession, and that rule is the only reason
 * the register exists.
 */
public final class NonconformanceRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public NonconformanceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** A row back into the report, decided or closed or neither. */
    public static Report rowToReport(NonconformanceRow row) {
        return new Report(
                row.getReference(),
                row.getJob(),
                row.getItem(),
                row.getRequirement(),
                row.getAccount(),
                row.getRaisedOn(),
                row.getRaisedBy(),
                row.getDisposition().filter(value -> !value.isEmpty()).map(Disposition::fromValue),
                row.getConcession(),
                row.getRemoval(),
                row.getClosedOn(),
                row.getClosedBy().orElse(""));
    }

    /** Write what can change about a report onto its row: the decision and the close. */
    public static void writeBack(NonconformanceRow row, Report report) {
        row.setDisposition(report.disposition().map(Disposition::value).orElse(null));
        row.setConcession(report.concession());
        row.setRemoval(report.removal());
        row.setClosedOn(report.closedOn().orElse(null));
        row.setClosedBy(report.closedBy().isEmpty() ? null : report.closedBy());
    }

    /**
     * Put a report on the register.
     *
     * @throws ConflictError for a reference already on it
     */
    public NonconformanceRow raiseReport(Report report) {
        if (find(report.reference()).isPresent()) {
            throw new ConflictError(
                    "that reference is already on the register", Context.of("reference", report.reference()));
        }
        NonconformanceRow row = new NonconformanceRow();
        row.setReference(report.reference());
        row.setJob(report.job());
        row.setItem(report.item());
        row.setRequirement(report.requirement());
        row.setAccount(report.account());
        row.setRaisedOn(report.raisedOn());
        row.setRaisedBy(report.raisedBy());
        writeBack(row, report);
        entityManager.persist(row);
        return row;
    }

    /** The row of the report with that reference, if there is one. */
    public Optional<NonconformanceRow> find(String reference) {
        return Queries.first(entityManager
                .createQuery(
                        "select n from NonconformanceRow n where n.reference = :reference", NonconformanceRow.class)
                .setParameter("reference", reference));
    }

    /**
     * The row of the report with that reference.
     *
     * @throws NotFoundError when nobody raised it
     */
    public NonconformanceRow get(String reference) {
        return find(reference).orElseThrow(() -> new NotFoundError("non conformance report", reference));
    }

    /**
     * The report with that reference.
     *
     * @throws NotFoundError when nobody raised it
     */
    public Report report(String reference) {
        return rowToReport(get(reference));
    }

    /** {@link #decide(String, Disposition, String, String)} with no concession and no removal reference. */
    public Report decide(String reference, Disposition disposition) {
        return decide(reference, disposition, "", "");
    }

    /**
     * Decide the report through the domain's rules and write the decision back.
     *
     * @throws NotFoundError when nobody raised it
     * @throws ConflictError where {@link Report#decide(Disposition, String, String)} refuses
     */
    public Report decide(String reference, Disposition disposition, String concession, String removal) {
        NonconformanceRow row = get(reference);
        Report report = rowToReport(row);
        report.decide(disposition, concession, removal);
        writeBack(row, report);
        return report;
    }

    /**
     * Close the report through the domain's rules and write the close back.
     *
     * @throws NotFoundError when nobody raised it
     * @throws ConflictError where {@link Report#close(LocalDate, String)} refuses
     */
    public Report close(String reference, LocalDate on, String by) {
        NonconformanceRow row = get(reference);
        Report report = rowToReport(row);
        report.close(on, by);
        writeBack(row, report);
        return report;
    }

    /** Every report on one job, in the order raised, a day's by reference. */
    public List<Report> forJob(String job) {
        return entityManager
                .createQuery(
                        "select n from NonconformanceRow n where n.job = :job order by n.raisedOn, n.reference",
                        NonconformanceRow.class)
                .setParameter("job", job)
                .getResultList()
                .stream()
                .map(NonconformanceRepository::rowToReport)
                .toList();
    }

    /** Every report not yet closed, on any job, in the order raised. */
    public List<Report> openReports() {
        return entityManager
                .createQuery(
                        "select n from NonconformanceRow n where n.closedOn is null order by n.raisedOn, n.reference",
                        NonconformanceRow.class)
                .getResultList()
                .stream()
                .map(NonconformanceRepository::rowToReport)
                .toList();
    }

    /** The domain register for one job, so the rules run against real rows. */
    public Register registerFor(String job) {
        Register register = new Register();
        forJob(job).forEach(register::raiseReport);
        return register;
    }
}
