package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.quantities.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One joint, placed in one window.
 *
 * @param weld the weld
 * @param windowStartsAt when the window it was placed in opens, which names the window
 * @param startsAt when the source goes out for it
 * @param endsAt when the source is back
 */
public record Slot(String weld, LocalDateTime windowStartsAt, LocalDateTime startsAt, LocalDateTime endsAt) {

    private static final double MICROS_PER_SECOND = 1e6;
    private static final double MS_PER_SECOND = 1000;

    /** A slot. */
    public Slot {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(windowStartsAt, "windowStartsAt");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
    }

    /**
     * How long the slot is. The span is read in seconds, as the nearest double to the exact number of
     * seconds, and its milliseconds are then cut off rather than rounded, so a span whose seconds have
     * no exact binary form, one second and one millisecond say, reads a millisecond short.
     */
    public Duration length() {
        double seconds = ChronoUnit.MICROS.between(startsAt, endsAt) / MICROS_PER_SECOND;
        return new Duration((long) (seconds * MS_PER_SECOND));
    }
}
