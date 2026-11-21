package io.github.rkaushikdev.swagebay.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Whether the truck moves, and everything stopping it if it does not.
 *
 * @param deviceSerial the camera
 * @param sourceSerial the source going out in it
 * @param day the day asked about
 * @param sourceReasons everything against the source
 * @param deviceReasons everything against the camera
 * @param crewReasons everything against the people on the booking
 */
public record Clearance(
        String deviceSerial,
        String sourceSerial,
        LocalDate day,
        List<String> sourceReasons,
        List<String> deviceReasons,
        List<String> crewReasons) {

    /** A clearance; the lists are copied. */
    public Clearance {
        Objects.requireNonNull(deviceSerial, "deviceSerial");
        Objects.requireNonNull(sourceSerial, "sourceSerial");
        Objects.requireNonNull(day, "day");
        sourceReasons = List.copyOf(sourceReasons);
        deviceReasons = List.copyOf(deviceReasons);
        crewReasons = List.copyOf(crewReasons);
    }

    /** Every reason, the source first, then the device, then the crew. */
    public List<String> reasons() {
        List<String> all = new ArrayList<>(sourceReasons);
        all.addAll(deviceReasons);
        all.addAll(crewReasons);
        return List.copyOf(all);
    }

    /** Whether nothing stops the load leaving the shop. */
    public boolean cleared() {
        return reasons().isEmpty();
    }

    /** {@code D-77 with IR-5512 is clear for 2025-06-23}, or every reason joined with semicolons. */
    public String describe() {
        if (cleared()) {
            return deviceSerial + " with " + sourceSerial + " is clear for " + day;
        }
        return String.join("; ", reasons());
    }
}
