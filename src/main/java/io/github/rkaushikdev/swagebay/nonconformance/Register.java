package io.github.rkaushikdev.swagebay.nonconformance;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every non conformance report, and the lists somebody asks for on a Monday. */
public final class Register {

    private static final Comparator<Report> RAISED =
            Comparator.comparing(Report::raisedOn).thenComparing(Report::reference);

    private final Map<String, Report> reports = new LinkedHashMap<>();

    /** An empty register. */
    public Register() {}

    /** Every report by reference, in the order raised on the register. */
    public Map<String, Report> reports() {
        return Collections.unmodifiableMap(reports);
    }

    /**
     * Put a report on the register.
     *
     * @throws ConflictError for a reference already on it
     */
    public void raiseReport(Report report) {
        if (reports.containsKey(report.reference())) {
            throw new ConflictError(
                    "that reference is already on the register", Context.of("reference", report.reference()));
        }
        reports.put(report.reference(), report);
    }

    /**
     * The report with that reference.
     *
     * @throws ConflictError for a reference nobody raised
     */
    public Report get(String reference) {
        Report found = reports.get(reference);
        if (found == null) {
            throw new ConflictError("no such report", Context.of("reference", reference));
        }
        return found;
    }

    /** How many reports there are. */
    public int size() {
        return reports.size();
    }

    /** One job's reports, in the order they were raised, a day's by reference. */
    public List<Report> forJob(String job) {
        return reports.values().stream()
                .filter(report -> report.job().equals(job))
                .sorted(RAISED)
                .toList();
    }

    /** Every open report, in the order they were raised, a day's by reference. */
    public List<Report> openReports() {
        return reports.values().stream().filter(Report::isOpen).sorted(RAISED).toList();
    }

    /** The open reports that have been open past the month, in the order they were raised. */
    public List<Report> overdueOn(LocalDate on) {
        return openReports().stream().filter(report -> report.isOverdue(on)).toList();
    }

    /** The open reports with nothing decided, which is what the Monday meeting is about. */
    public List<Report> awaitingADecision() {
        return openReports().stream()
                .filter(report -> report.disposition().isEmpty())
                .toList();
    }

    /** How many reports carry each disposition, by its phrase, in the order each was first seen. */
    public Map<String, Integer> byDisposition() {
        Map<String, Integer> counted = new LinkedHashMap<>();
        reports.values().forEach(report -> report.disposition()
                .ifPresent(disposition -> counted.merge(disposition.toString(), 1, Integer::sum)));
        return Collections.unmodifiableMap(counted);
    }

    /** Every concession reference the client has signed against one job, sorted, each once. */
    public List<String> concessionsOn(String job) {
        return forJob(job).stream()
                .map(Report::concession)
                .filter(concession -> !concession.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    /** Why this job cannot be signed off, in the words the coordinator uses. */
    public List<String> blocksClosing(String job) {
        List<String> reasons = new ArrayList<>();
        for (Report report : forJob(job)) {
            if (report.isOpen()) {
                reasons.add(report.reference() + " is still open");
            } else if (report.disposition().filter(Disposition.REJECT::equals).isPresent()
                    && report.removal().isEmpty()) {
                reasons.add(report.reference() + " rejected an item with no removal reference");
            }
        }
        return List.copyOf(reasons);
    }
}
