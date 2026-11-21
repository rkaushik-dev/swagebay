package io.github.rkaushikdev.swagebay.safety;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Every device inspection, and the one question asked of it every morning: may this device go out. */
public final class MaintenanceRegister {

    private final List<Inspection> inspections = new ArrayList<>();

    /** An empty register. */
    public MaintenanceRegister() {}

    /** Every inspection, in the order recorded. */
    public List<Inspection> inspections() {
        return Collections.unmodifiableList(inspections);
    }

    /** Put an inspection on file. */
    public void record(Inspection inspection) {
        inspections.add(inspection);
    }

    /** One device's inspections in date order, those on one day in the order recorded. */
    public List<Inspection> forDevice(String serial) {
        return inspections.stream()
                .filter(inspection -> inspection.deviceSerial().equals(serial))
                .sorted(Comparator.comparing(Inspection::performedOn))
                .toList();
    }

    /** The last quarterly on file for a device, whenever it was done. */
    public Optional<Inspection> latestQuarterly(String serial) {
        return lastQuarterly(serial, Optional.empty());
    }

    /**
     * The last quarterly on or before a day, not the last one on file, so a device whose quarterly
     * was done on the sixteenth does not read as covered on the fifteenth. That matters in a back
     * dated booking and in every clearance asked about a past day.
     */
    public Optional<Inspection> latestQuarterly(String serial, LocalDate on) {
        return lastQuarterly(serial, Optional.of(on));
    }

    /** The pre use check done on a device on that day, the first recorded of any. */
    public Optional<Inspection> checkOn(String serial, LocalDate day) {
        return forDevice(serial).stream()
                .filter(inspection ->
                        !inspection.isQuarterly() && inspection.performedOn().equals(day))
                .findFirst();
    }

    /**
     * Everything stopping this device going out that morning: the quarterly as of that day, missing,
     * run out or blocked by what it found, then the pre use check, missing or blocked.
     */
    public List<String> reasonsNotToUse(String serial, LocalDate day) {
        List<String> reasons = new ArrayList<>();
        Optional<Inspection> latest = latestQuarterly(serial, day);
        if (latest.isEmpty()) {
            reasons.add("no quarterly inspection on record");
        } else {
            Optional<LocalDate> expires = latest.get().expiresOn();
            if (expires.isPresent() && !day.isBefore(expires.get())) {
                reasons.add("quarterly ran out on " + expires.get());
            } else if (!latest.get().blockingDefects().isEmpty()) {
                reasons.add("last quarterly found "
                        + latest.get().blockingDefects().get(0).describe());
            }
        }
        Optional<Inspection> check = checkOn(serial, day);
        if (check.isEmpty()) {
            reasons.add("no pre use check that morning");
        } else if (!check.get().blockingDefects().isEmpty()) {
            reasons.add(check.get().blockingDefects().get(0).describe());
        }
        return List.copyOf(reasons);
    }

    /** Whether nothing stops the device going out that morning. */
    public boolean mayGoOut(String serial, LocalDate day) {
        return reasonsNotToUse(serial, day).isEmpty();
    }

    /** When the last quarterly on file runs out, if there is one. */
    public Optional<LocalDate> quarterlyDueBy(String serial) {
        return latestQuarterly(serial).flatMap(Inspection::expiresOn);
    }

    /** When the last quarterly on or before that day runs out, if there is one. */
    public Optional<LocalDate> quarterlyDueBy(String serial, LocalDate on) {
        return latestQuarterly(serial, on).flatMap(Inspection::expiresOn);
    }

    /**
     * The devices of those with no quarterly covering that day, sorted: none on or before it, or the
     * last one run out. A quarterly done later is not credited.
     */
    public List<String> overdueOn(List<String> serials, LocalDate day) {
        return serials.stream()
                .filter(serial -> quarterlyDueBy(serial, day)
                        .map(due -> !day.isBefore(due))
                        .orElse(true))
                .sorted()
                .toList();
    }

    private Optional<Inspection> lastQuarterly(String serial, Optional<LocalDate> on) {
        List<Inspection> found = forDevice(serial).stream()
                .filter(inspection -> inspection.isQuarterly()
                        && on.map(day -> !inspection.performedOn().isAfter(day)).orElse(true))
                .toList();
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
    }
}
