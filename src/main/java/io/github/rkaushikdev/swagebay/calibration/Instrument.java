package io.github.rkaushikdev.swagebay.calibration;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.time.Duration;

/**
 * What kind of thing an instrument is, which sets its default interval.
 *
 * <p>The intervals are all different: a survey meter is annual, a step wedge is replaced rather
 * than calibrated, and a viewer goes two years.
 */
public enum Instrument {
    SURVEY_METER("survey meter", 365),
    DENSITOMETER("densitometer", 365),
    STEP_WEDGE("step wedge", 730),
    DOSIMETER("dosimeter", 365),
    ALARM("alarming ratemeter", 365),
    VIEWER("viewer", 730);

    private final String value;
    private final Duration defaultInterval;

    Instrument(String value, int intervalDays) {
        this.value = value;
        this.defaultInterval = Duration.ofDays(intervalDays);
    }

    /**
     * The kind with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no kind of instrument
     */
    public static Instrument fromValue(String value) {
        for (Instrument kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no instrument is stored as " + Repr.of(value));
    }

    /** The stored name, {@code alarming ratemeter}. */
    public String value() {
        return value;
    }

    /** How long a calibration of this kind lasts when the certificate does not say. */
    public Duration defaultInterval() {
        return defaultInterval;
    }

    /** Whether this kind is thrown away and bought again rather than sent to a laboratory. */
    public boolean isReplacedRatherThanCalibrated() {
        return this == STEP_WEDGE;
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
