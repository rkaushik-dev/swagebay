package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Film retention, which is a legal obligation with a physical filing cabinet behind it.
 *
 * <p>The archive is the filing cabinet as a record: every radiograph filed once, by its number.
 * Location is part of the record because a film that cannot be found has not been retained, and a
 * film marked as retained with no location is reported as a fault rather than as stored. Every list
 * the archive gives of its own films comes back in radiograph number order.
 */
public final class Archive {

    private final Map<String, Filed> filed = new LinkedHashMap<>();

    /** An empty cabinet. */
    public Archive() {}

    /** Every film filed, by radiograph number, in the order filed. */
    public Map<String, Filed> filed() {
        return Collections.unmodifiableMap(filed);
    }

    /**
     * File a radiograph.
     *
     * @throws ConflictError when that radiograph is already filed
     */
    public void file(Filed entry) {
        if (filed.containsKey(entry.radiograph())) {
            throw new ConflictError("that radiograph is already filed", Context.of("radiograph", entry.radiograph()));
        }
        filed.put(entry.radiograph(), entry);
    }

    /** The film filed under that number, if there is one. */
    public Optional<Filed> find(String radiograph) {
        return Optional.ofNullable(filed.get(radiograph));
    }

    /** The films shot on one job, in order. */
    public List<Filed> forJob(String job) {
        return inOrder().filter(entry -> entry.job().equals(job)).toList();
    }

    /** The radiographs that may be destroyed on that day; nothing without an end date is ever on it. */
    public List<String> dueForDestruction(LocalDate day) {
        return inOrder()
                .filter(entry -> entry.isDueOn(day))
                .map(Filed::radiograph)
                .toList();
    }

    /** The radiographs kept for a life rather than to a date. */
    public List<String> keptIndefinitely() {
        return inOrder()
                .filter(entry -> !entry.retention().hasAnEndDate())
                .map(Filed::radiograph)
                .toList();
    }

    /** Every film's faults, in order. */
    public List<String> faults() {
        return inOrder().flatMap(entry -> entry.faults().stream()).toList();
    }

    /** Radiographs a job says exist that are not in the cabinet, in the order the job gives them. */
    public List<String> missingFrom(Collection<String> expected) {
        return expected.stream().filter(number -> !filed.containsKey(number)).toList();
    }

    private Stream<Filed> inOrder() {
        return filed.values().stream().sorted(Comparator.comparing(Filed::radiograph));
    }
}
