package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One device, on one day, at one place, with the crew that ran it: a line of the utilisation log.
 *
 * <p>It is the only record that ties a person to a source on a day, so it is what a dose
 * reconstruction is built from when a badge comes back high. A device does not operate itself, so a
 * use names at least one radiographer.
 *
 * @param usedOn the day
 * @param deviceSerial the device, never blank
 * @param sourceSerial the source that was in it, never blank
 * @param site where the work was, never blank
 * @param radiographers the badges of everybody who operated it, at least one, each once
 * @param exposures how many exposures were made, never negative
 */
public record Use(
        LocalDate usedOn,
        String deviceSerial,
        String sourceSerial,
        String site,
        List<String> radiographers,
        int exposures) {

    /** How many years the log is kept. */
    public static final int RETENTION_YEARS = 3;

    /** How many days the log is kept. */
    public static final int RETENTION_DAYS = RETENTION_YEARS * 365;

    /**
     * A use; the badges are copied.
     *
     * @throws ValidationError without a device, a source or a site, with nobody on it, with a blank
     *     or repeated badge, or with negative exposures
     */
    public Use {
        Objects.requireNonNull(usedOn, "usedOn");
        radiographers = List.copyOf(radiographers);
        if (Text.strip(deviceSerial).isEmpty() || Text.strip(sourceSerial).isEmpty()) {
            throw new ValidationError("a use names the device and the source in it");
        }
        if (Text.strip(site).isEmpty()) {
            throw new ValidationError("a use names where the work was");
        }
        if (radiographers.isEmpty()) {
            throw new ValidationError("a device does not operate itself", Context.of("device", deviceSerial));
        }
        if (radiographers.stream().anyMatch(Text::isBlank)) {
            throw new ValidationError("a blank badge is not a radiographer");
        }
        if (new HashSet<>(radiographers).size() != radiographers.size()) {
            throw new ValidationError("the same badge is on that entry twice", Context.of("device", deviceSerial));
        }
        if (exposures < 0) {
            throw new ValidationError("exposures do not go backwards", Context.of("value", String.valueOf(exposures)));
        }
    }

    /** A day out and back with nothing shot. */
    public Use(LocalDate usedOn, String deviceSerial, String sourceSerial, String site, List<String> radiographers) {
        this(usedOn, deviceSerial, sourceSerial, site, radiographers, 0);
    }

    /** Whether it was out and back with nothing shot, which still has to be logged. */
    public boolean wasAMobilisation() {
        return exposures == 0;
    }

    /** The day the entry may be destroyed. */
    public LocalDate destroyAfter() {
        return usedOn.plusDays(RETENTION_DAYS);
    }

    /**
     * The entry as an inspector reads it, the crew sorted: {@code 2025-06-09 D-77 with IR-5512 at
     * Come By Chance, 14 exposures, 0142, 0201}.
     */
    public String describe() {
        String crew = String.join(", ", radiographers.stream().sorted().toList());
        return usedOn + " " + deviceSerial + " with " + sourceSerial + " at " + site + ", " + exposures + " exposures, "
                + crew;
    }
}
