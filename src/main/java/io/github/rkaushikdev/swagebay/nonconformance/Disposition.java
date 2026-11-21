package io.github.rkaushikdev.swagebay.nonconformance;

import io.github.rkaushikdev.swagebay.util.Repr;

/**
 * What was decided about work that did not meet the specification. Four of them and only four,
 * because a shop that invents a fifth ends up with a category meaning "we talked about it".
 */
public enum Disposition {
    REPAIR("repair"),
    REWORK("rework"),
    USE_AS_IS("use as is"),
    REJECT("reject");

    private final String value;

    Disposition(String value) {
        this.value = value;
    }

    /**
     * The disposition with that stored phrase.
     *
     * @throws IllegalArgumentException for a phrase that names no disposition
     */
    public static Disposition fromValue(String value) {
        for (Disposition disposition : values()) {
            if (disposition.value.equals(value)) {
                return disposition;
            }
        }
        throw new IllegalArgumentException("no disposition is stored as " + Repr.of(value));
    }

    /** The stored phrase, {@code use as is}. */
    public String value() {
        return value;
    }

    /**
     * Whether it needs a concession signed by whoever owns the specification: use as is leaves work
     * outside it, and a repair is a change to a welded joint the client is buying.
     */
    public boolean needsConcession() {
        return this == USE_AS_IS || this == REPAIR;
    }

    /** Whether it needs a removal reference before the report can close. */
    public boolean needsRemoval() {
        return this == REJECT;
    }

    /** The stored phrase. */
    @Override
    public String toString() {
        return value;
    }
}
