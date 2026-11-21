package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Welder qualifications, and the last time each welder used each process.
 *
 * <p>Continuity is the part that catches people. A qualification lapses if the welder has not used
 * that process for six months, and it lapses silently: there is no certificate that expires. The
 * only way to know is to look at when they last welded with it, which is why that date is kept here
 * and why the lapsed list exists.
 */
public final class Register {

    private final List<Qualification> qualifications = new ArrayList<>();
    private final Map<WelderProcess, LocalDate> lastUsed = new LinkedHashMap<>();

    /** An empty register. */
    public Register() {}

    /** Every qualification, in the order recorded. */
    public List<Qualification> qualifications() {
        return Collections.unmodifiableList(qualifications);
    }

    /** The last day each welder used each process they hold, in the order first qualified. */
    public Map<WelderProcess, LocalDate> lastUsed() {
        return Collections.unmodifiableMap(lastUsed);
    }

    /**
     * Record a qualification. The first one for a welder and process starts its continuity on the day
     * of the test.
     *
     * @throws ConflictError for the same welder, process and test day already recorded
     */
    public void add(Qualification qualification) {
        WelderProcess key = new WelderProcess(qualification.welder(), qualification.process());
        boolean recorded = qualifications.stream()
                .anyMatch(held -> held.welder().equals(key.welder())
                        && held.process() == key.process()
                        && held.qualifiedOn().equals(qualification.qualifiedOn()));
        if (recorded) {
            throw new ConflictError(
                    "that qualification is already recorded", Context.of("welder", qualification.welder()));
        }
        qualifications.add(qualification);
        lastUsed.putIfAbsent(key, qualification.qualifiedOn());
    }

    /**
     * Record that a welder used a process on a day. An earlier day than the last one on record
     * changes nothing.
     *
     * @throws ConflictError for a welder not qualified on that process
     */
    public void recordUse(String welder, WeldingProcess process, LocalDate on) {
        WelderProcess key = new WelderProcess(welder, process);
        LocalDate last = lastUsed.get(key);
        if (last == null) {
            throw new ConflictError("that welder is not qualified for that process", Context.of("welder", welder));
        }
        if (on.isAfter(last)) {
            lastUsed.put(key, on);
        }
    }

    /** Every qualification one welder holds, in the order recorded. */
    public List<Qualification> forWelder(String welder) {
        return qualifications.stream()
                .filter(qualification -> qualification.welder().equals(welder))
                .toList();
    }

    /** Whether the welder has used the process within {@link Welding#CONTINUITY_DAYS} of that day. */
    public boolean isCurrentOn(String welder, WeldingProcess process, LocalDate day) {
        LocalDate used = lastUsed.get(new WelderProcess(welder, process));
        return used != null && ChronoUnit.DAYS.between(used, day) < Welding.CONTINUITY_DAYS;
    }

    /**
     * Whether the welder may make that weld on that day: current on the process, and holding a
     * qualification on it that covers the position, the wall and the material group.
     */
    public boolean mayWeld(
            String welder, WeldingProcess process, Position position, Length wall, String group, LocalDate day) {
        if (!isCurrentOn(welder, process, day)) {
            return false;
        }
        return forWelder(welder).stream()
                .anyMatch(qualification ->
                        qualification.process() == process && qualification.covers(position, wall, group));
    }

    /** Every welder and process that has gone quiet by that day, by welder and then process name. */
    public List<WelderProcess> lapsedOn(LocalDate day) {
        return lastUsed.keySet().stream()
                .sorted(Comparator.comparing(WelderProcess::welder)
                        .thenComparing(key -> key.process().name()))
                .filter(key -> !isCurrentOn(key.welder(), key.process(), day))
                .toList();
    }

    /** The first day the welder is no longer current on the process, if they are qualified on it. */
    public Optional<LocalDate> lapsesOn(String welder, WeldingProcess process) {
        return Optional.ofNullable(lastUsed.get(new WelderProcess(welder, process)))
                .map(used -> used.plusDays(Welding.CONTINUITY_DAYS));
    }
}
