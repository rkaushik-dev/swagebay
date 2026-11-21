package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;

/** The transport label a package carries, quietest first. */
public enum Label {
    WHITE_I("White-I"),
    YELLOW_II("Yellow-II"),
    YELLOW_III("Yellow-III");

    private final String value;

    Label(String value) {
        this.value = value;
    }

    /**
     * The label with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no label
     */
    public static Label fromValue(String value) {
        for (Label label : values()) {
            if (label.value.equals(value)) {
                return label;
            }
        }
        throw new IllegalArgumentException("no transport label is stored as " + Repr.of(value));
    }

    /** The name printed on it, {@code Yellow-III}. */
    public String value() {
        return value;
    }

    /** The name printed on it. */
    @Override
    public String toString() {
        return value;
    }
}
