package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A source going out on a job.
 *
 * @param sourceSerial the source booked out
 * @param deviceSerial the camera it goes out in
 * @param crewBadge the badge of the lead hand taking it
 * @param site where it is going
 * @param wentOut the day it leaves the shop
 * @param crew the people on the truck, whose cards and eyes the gate checks; none when only the badge
 *     is known
 */
public record Booking(
        String sourceSerial, String deviceSerial, String crewBadge, String site, LocalDate wentOut, List<Person> crew) {

    /**
     * A booking; the crew is copied.
     *
     * @throws ValidationError naming the crew badge or the site, whichever is blank first
     */
    public Booking {
        Objects.requireNonNull(sourceSerial, "sourceSerial");
        Objects.requireNonNull(deviceSerial, "deviceSerial");
        Objects.requireNonNull(wentOut, "wentOut");
        crew = List.copyOf(crew);
        required("crew_badge", crewBadge);
        required("site", site);
    }

    /** A booking that names the lead hand by badge and nobody else. */
    public Booking(String sourceSerial, String deviceSerial, String crewBadge, String site, LocalDate wentOut) {
        this(sourceSerial, deviceSerial, crewBadge, site, wentOut, List.of());
    }

    private static void required(String name, String value) {
        if (Text.isBlank(value)) {
            throw new ValidationError("a booking needs this filled in", Context.of("field", name));
        }
    }
}
