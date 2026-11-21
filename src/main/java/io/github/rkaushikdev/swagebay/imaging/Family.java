package io.github.rkaushikdev.swagebay.imaging;

/**
 * Which indicator family the job specification calls for.
 *
 * <p>The two families are measured against different thicknesses. The hole table is keyed on the
 * nominal single wall; the wire tables are keyed on the penetrated thickness, which on a double
 * wall shot is a very different number.
 */
public enum Family {
    HOLE("hole type"),
    WIRE("wire type");

    private final String value;

    Family(String value) {
        this.value = value;
    }

    /** The family as a specification names it. */
    public String value() {
        return value;
    }

    /** Whether the family's table reads penetrated thickness rather than the wall. */
    public boolean measuredOnPenetratedThickness() {
        return this == WIRE;
    }

    @Override
    public String toString() {
        return value;
    }
}
