package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.sources.Loading;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The utilisation log, which is the record an inspector asks for first.
 *
 * <p>For every exposure device, for every day it was used, the log names the date, the device, the
 * source that was in it, where the work was, and every radiographer who operated it, three years of
 * them. It is what proves a device was not out when somebody says it was, and a log with a gap in it
 * is worse than no log, because the gap is the day everyone will ask about. A use naming a source the
 * device was not holding is refused here rather than left to the reader, because the loading record
 * already knows.
 */
public final class UtilisationLog {

    private static final Comparator<Use> BY_DAY = Comparator.comparing(Use::usedOn);

    private final List<Use> uses = new ArrayList<>();

    /** An empty log. */
    public UtilisationLog() {}

    /** Every use, in the order recorded. */
    public List<Use> uses() {
        return Collections.unmodifiableList(uses);
    }

    /** Add a use without checking it against a loading record. */
    public void record(Use use) {
        uses.add(use);
    }

    /**
     * Add a use, refusing one that contradicts the loading record. A device the loading record knows
     * nothing about is not second guessed.
     *
     * @throws ConflictError when the device was holding a different source
     */
    public void record(Use use, Loading loading) {
        Optional<String> holding = loading.sourceIn(use.deviceSerial());
        if (holding.isPresent() && !holding.get().equals(use.sourceSerial())) {
            throw new ConflictError(
                    "that device was not holding that source",
                    Context.of("device", use.deviceSerial(), "holding", holding.get(), "claimed", use.sourceSerial()));
        }
        uses.add(use);
    }

    /** One device's uses from one day to another, both included, in date order. */
    public List<Use> forDevice(String serial, LocalDate since, LocalDate until) {
        return between(since, until, use -> use.deviceSerial().equals(serial));
    }

    /** One source's uses from one day to another, both included, in date order, whatever device it was in. */
    public List<Use> forSource(String serial, LocalDate since, LocalDate until) {
        return between(since, until, use -> use.sourceSerial().equals(serial));
    }

    /** The uses one badge was on from one day to another, both included, in date order. */
    public List<Use> forRadiographer(String badge, LocalDate since, LocalDate until) {
        return between(since, until, use -> use.radiographers().contains(badge));
    }

    /** The distinct days a device was used from one day to another; two entries on one day are one day. */
    public int daysUsed(String serial, LocalDate since, LocalDate until) {
        Set<LocalDate> days = new HashSet<>();
        forDevice(serial, since, until).forEach(use -> days.add(use.usedOn()));
        return days.size();
    }

    /** The exposures made with each device from one day to another, in the order the devices first appear. */
    public Map<String, Integer> exposuresByDevice(LocalDate since, LocalDate until) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Use use : uses) {
            if (within(use, since, until)) {
                counted.merge(use.deviceSerial(), use.exposures(), Integer::sum);
            }
        }
        return Collections.unmodifiableMap(counted);
    }

    /** The sites one badge worked from one day to another, sorted, each once. */
    public List<String> sitesWorked(String badge, LocalDate since, LocalDate until) {
        return forRadiographer(badge, since, until).stream()
                .map(Use::site)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Days a device was out on a trip with nothing in the log for it, by device and then day, each
     * once.
     *
     * <p>This is the check the log exists for. A trip says the device left the shop; the log says what
     * it did. A day with a trip and no entry is a day nobody wrote down. A trip still out is checked up
     * to the day it left.
     */
    public List<DeviceDay> unloggedDays(List<Trip> trips) {
        Set<DeviceDay> logged = new HashSet<>();
        uses.forEach(use -> logged.add(new DeviceDay(use.deviceSerial(), use.usedOn())));
        Set<DeviceDay> missing = new TreeSet<>();
        for (Trip trip : trips) {
            LocalDate last = trip.cameBack().orElse(trip.wentOut());
            for (LocalDate day = trip.wentOut(); !day.isAfter(last); day = day.plusDays(1)) {
                DeviceDay out = new DeviceDay(trip.deviceSerial(), day);
                if (!logged.contains(out)) {
                    missing.add(out);
                }
            }
        }
        return List.copyOf(missing);
    }

    /** The uses whose retention has run out by that day, in date order. */
    public List<Use> dueForDestruction(LocalDate on) {
        return uses.stream()
                .filter(use -> !use.destroyAfter().isAfter(on))
                .sorted(BY_DAY)
                .toList();
    }

    /** The uses dated after that day, in date order, for a late amendment. */
    public List<Use> entriesAfter(LocalDate on) {
        return uses.stream()
                .filter(use -> use.usedOn().isAfter(on))
                .sorted(BY_DAY)
                .toList();
    }

    private List<Use> between(LocalDate since, LocalDate until, Predicate<Use> condition) {
        return uses.stream()
                .filter(use -> condition.test(use) && within(use, since, until))
                .sorted(BY_DAY)
                .toList();
    }

    private static boolean within(Use use, LocalDate since, LocalDate until) {
        return !use.usedOn().isBefore(since) && !use.usedOn().isAfter(until);
    }
}
