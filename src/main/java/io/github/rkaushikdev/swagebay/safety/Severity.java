package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;

/** What a defect on an inspection item does to the day. */
public enum Severity {
    STOPS_THE_DEVICE("stops the device"),
    WORK_AROUND("work around");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    /**
     * The severity with that stored phrase.
     *
     * @throws IllegalArgumentException for a phrase that names no severity
     */
    public static Severity fromValue(String value) {
        for (Severity severity : values()) {
            if (severity.value.equals(value)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("no severity is stored as " + Repr.of(value));
    }

    /** The stored phrase, {@code stops the device}. */
    public String value() {
        return value;
    }

    /** The stored phrase. */
    @Override
    public String toString() {
        return value;
    }
}
