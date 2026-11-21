package io.github.rkaushikdev.swagebay.reporting;

import java.util.OptionalInt;

/**
 * How long a radiograph is kept, which is three different kinds of answer.
 *
 * <p>A pipeline under a road is kept for the life of the line, which nobody can put a date on, so it
 * is marked as indefinite rather than given a made up date. A pressure vessel is the vessel's life
 * plus a margin. Ordinary structural work is five years. A period that cannot be expressed as a date
 * has to be able to say so, and a system that forces one ends up with radiographs quietly destroyed
 * in 2049 because somebody typed twenty five years in 2024.
 */
public enum Retention {
    FIVE_YEARS("five years"),
    VESSEL_LIFE("life of the vessel"),
    INDEFINITE("life of the line");

    private static final int FIVE_YEARS_DAYS = 1826;

    private final String value;

    Retention(String value) {
        this.value = value;
    }

    /** The retention as the filing card writes it. */
    public String value() {
        return value;
    }

    /** Whether the retention runs out on a date. */
    public boolean hasAnEndDate() {
        return this == FIVE_YEARS;
    }

    /** How many days the film is kept, where that is a number at all. */
    public OptionalInt days() {
        return this == FIVE_YEARS ? OptionalInt.of(FIVE_YEARS_DAYS) : OptionalInt.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
