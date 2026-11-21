package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import java.time.Duration;
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
 * Every source the shop holds, plus the two records that account for them.
 *
 * <p>The regulator cares that the licensee can say, at any moment and without looking anything
 * up, where each sealed source is and who is responsible for it. Two records carry that. The
 * utilization log says which source went out in which device, with which crew, to which site, and
 * when it came back. The quarterly inventory is a physical check: somebody lays eyes on the source,
 * or on the device containing it, and signs.
 *
 * <p>A source that is out on a job at inventory time is not missing, it is out on a job, and the
 * log says where. A source with no open log entry and no sighting inside the quarter is missing,
 * and that is a phone call to the regulator inside twenty four hours.
 */
public final class Inventory {

    /** A quarter, which the licence condition writes as three months. */
    public static final Duration INTERVAL = Duration.ofDays(92);

    private final Map<String, SealedSource> sources = new LinkedHashMap<>();
    private final Map<String, DeviceUnit> devices = new LinkedHashMap<>();
    private final List<Trip> trips = new ArrayList<>();
    private final List<Sighting> sightings = new ArrayList<>();

    /** An empty inventory. */
    public Inventory() {}

    /** Every source on the inventory by serial, in the order they were added. */
    public Map<String, SealedSource> sources() {
        return Collections.unmodifiableMap(sources);
    }

    /** Every device on the inventory by serial, in the order they were added. */
    public Map<String, DeviceUnit> devices() {
        return Collections.unmodifiableMap(devices);
    }

    /** Every trip in the utilization log, in the order they went out. */
    public List<Trip> trips() {
        return Collections.unmodifiableList(trips);
    }

    /** Every sighting, in the order they were signed in. */
    public List<Sighting> sightings() {
        return Collections.unmodifiableList(sightings);
    }

    /**
     * Put a source on the inventory.
     *
     * @throws ConflictError for a serial already on it
     */
    public void addSource(SealedSource source) {
        if (sources.containsKey(source.serial())) {
            throw new ConflictError("that source is already on the inventory", Context.of("serial", source.serial()));
        }
        sources.put(source.serial(), source);
    }

    /**
     * Put a device on the inventory.
     *
     * @throws ConflictError for a serial already on it
     */
    public void addDevice(DeviceUnit unit) {
        if (devices.containsKey(unit.serial())) {
            throw new ConflictError("that device is already on the inventory", Context.of("serial", unit.serial()));
        }
        devices.put(unit.serial(), unit);
    }

    /** Sign a sighting in; a sighting is a fact on the day, so nothing about it is refused here. */
    public void addSighting(Sighting sighting) {
        sightings.add(sighting);
    }

    /**
     * Log a source going out.
     *
     * @throws ValidationError for a source or a device that is not on the inventory
     * @throws ConflictError for a source already out on a job
     */
    public void sendOut(Trip trip) {
        if (!sources.containsKey(trip.sourceSerial())) {
            throw new ValidationError("that source is not on the inventory", Context.of("serial", trip.sourceSerial()));
        }
        if (!devices.containsKey(trip.deviceSerial())) {
            throw new ValidationError("that device is not on the inventory", Context.of("serial", trip.deviceSerial()));
        }
        if (openTrip(trip.sourceSerial()).isPresent()) {
            throw new ConflictError("that source is already out on a job", Context.of("serial", trip.sourceSerial()));
        }
        trips.add(trip);
    }

    /**
     * Close the open trip of a source on the day it came back.
     *
     * @throws ConflictError for a source that is not out
     * @throws ValidationError for a day before it went out
     */
    public void bringBack(String sourceSerial, LocalDate on) {
        Trip trip = openTrip(sourceSerial)
                .orElseThrow(
                        () -> new ConflictError("that source is not out on a job", Context.of("serial", sourceSerial)));
        if (on.isBefore(trip.wentOut())) {
            throw new ValidationError("a source cannot come back before it went out");
        }
        trip.recordReturn(on);
    }

    /** The trip a source is out on, if it is out. */
    public Optional<Trip> openTrip(String sourceSerial) {
        return trips.stream()
                .filter(trip -> trip.sourceSerial().equals(sourceSerial) && trip.isOpen())
                .findFirst();
    }

    /** Where a source was on a day: the site of the first trip covering it, or the shop. */
    public String locationOf(String sourceSerial, LocalDate day) {
        return trips.stream()
                .filter(trip -> trip.sourceSerial().equals(sourceSerial) && trip.covers(day))
                .findFirst()
                .map(Trip::site)
                .orElse("shop");
    }

    /** The most recent sighting of a source, the first signed in of any on the same day. */
    public Optional<Sighting> lastSighting(String sourceSerial) {
        return sightings.stream()
                .filter(sighting -> sighting.sourceSerial().equals(sourceSerial))
                .max(Comparator.comparing(Sighting::seenOn));
    }

    /** The sources, by sorted serial, with neither an open job nor a sighting inside the quarter. */
    public List<String> unaccountedOn(LocalDate day) {
        List<String> out = new ArrayList<>();
        for (String serial : sources.keySet().stream().sorted().toList()) {
            if (openTrip(serial).isPresent()) {
                continue;
            }
            Optional<Sighting> seen = lastSighting(serial);
            if (seen.isEmpty() || sinceSeen(seen.get(), day).compareTo(INTERVAL) > 0) {
                out.add(serial);
            }
        }
        return List.copyOf(out);
    }

    /** Every exposure logged against a crew badge. */
    public int exposuresBy(String crewBadge) {
        return trips.stream()
                .filter(trip -> trip.crewBadge().equals(crewBadge))
                .mapToInt(Trip::exposures)
                .sum();
    }

    private static Duration sinceSeen(Sighting seen, LocalDate day) {
        return Duration.ofDays(ChronoUnit.DAYS.between(seen.seenOn(), day));
    }
}
