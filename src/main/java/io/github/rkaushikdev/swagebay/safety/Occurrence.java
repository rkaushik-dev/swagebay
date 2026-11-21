package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.time.Duration;

/**
 * What went wrong, which sets the clock for telling the regulator. A source that will not retract
 * is an immediate call; a dose over a limit or a lost device is twenty four hours; a survey meter
 * found out of calibration after the fact is a thirty day written report.
 */
public enum Occurrence {
    SOURCE_DISCONNECTED("source will not retract", Duration.ofHours(1)),
    OVEREXPOSURE("dose over a limit", Duration.ofHours(24)),
    LOST_DEVICE("device lost or stolen", Duration.ofHours(24)),
    DAMAGED_DEVICE("device damaged", Duration.ofHours(24)),
    UNCONTROLLED_AREA("boundary breached by a member of the public", Duration.ofHours(24)),
    METER_OUT_OF_CALIBRATION("survey meter found out of calibration", Duration.ofDays(30)),
    LEAK_TEST_FAILURE("leak test over the limit", Duration.ofDays(5));

    private static final Duration IMMEDIATE = Duration.ofHours(1);
    private static final Duration WRITTEN = Duration.ofDays(30);

    private final String value;
    private final Duration deadline;

    Occurrence(String value, Duration deadline) {
        this.value = value;
        this.deadline = deadline;
    }

    /**
     * The occurrence with that stored phrase.
     *
     * @throws IllegalArgumentException for a phrase that names no occurrence
     */
    public static Occurrence fromValue(String value) {
        for (Occurrence occurrence : values()) {
            if (occurrence.value.equals(value)) {
                return occurrence;
            }
        }
        throw new IllegalArgumentException("no occurrence is stored as " + Repr.of(value));
    }

    /** The stored phrase, {@code source will not retract}. */
    public String value() {
        return value;
    }

    /** How long after it is found the regulator has to be told. */
    public Duration deadline() {
        return deadline;
    }

    /** Whether it is a call to make now: an hour or less. */
    public boolean isImmediate() {
        return deadline.compareTo(IMMEDIATE) <= 0;
    }

    /** Whether it is owed as a written report: thirty days or more. */
    public boolean needsAWrittenReport() {
        return deadline.compareTo(WRITTEN) >= 0;
    }

    /** The stored phrase. */
    @Override
    public String toString() {
        return value;
    }
}
