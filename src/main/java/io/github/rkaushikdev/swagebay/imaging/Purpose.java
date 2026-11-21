package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.util.Arrays;

/** What a stored image is for: the record of the exposure, or a copy to look at. */
public enum Purpose {
    RECORD("record"),
    VIEWING("viewing");

    private final String value;

    Purpose(String value) {
        this.value = value;
    }

    /**
     * The purpose with that stored value.
     *
     * @throws IllegalArgumentException for a value that names no purpose
     */
    public static Purpose fromValue(String value) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no purpose is stored as " + Repr.of(value)));
    }

    /** The stored value. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
