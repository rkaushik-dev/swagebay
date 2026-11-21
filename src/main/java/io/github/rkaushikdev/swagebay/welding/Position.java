package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The welding positions, in the order they get harder. A test weld in one position qualifies that
 * position and every easier one.
 */
public enum Position {
    FLAT("1G"),
    HORIZONTAL("2G"),
    VERTICAL("5G"),
    ALL("6G");

    private final String value;

    Position(String value) {
        this.value = value;
    }

    /**
     * The position with that designation.
     *
     * @throws IllegalArgumentException for a designation that names no position
     */
    public static Position fromValue(String value) {
        for (Position position : values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("no welding position is stored as " + Repr.of(value));
    }

    /** The designation, {@code 6G}. */
    public String value() {
        return value;
    }

    /** What making a test weld in this position buys: this position and every easier one. */
    public Set<Position> qualifies() {
        return Collections.unmodifiableSet(EnumSet.range(FLAT, this));
    }

    /** The designation. */
    @Override
    public String toString() {
        return value;
    }
}
