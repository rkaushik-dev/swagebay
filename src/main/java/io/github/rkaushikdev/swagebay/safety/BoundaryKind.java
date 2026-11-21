package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.util.Repr;

/** The postings the licence defines, softest first, each at the dose rate the licence sets. */
public enum BoundaryKind {
    RESTRICTED("restricted area", 2, "caution radiation area"),
    RADIATION_AREA("radiation area", 5, "caution radiation area"),
    HIGH_RADIATION("high radiation area", 100, "danger high radiation area");

    private final String value;
    private final DoseRate rate;
    private final String signReads;

    BoundaryKind(String value, long mrPerHour, String signReads) {
        this.value = value;
        this.rate = DoseRate.fromMrPerHour(mrPerHour);
        this.signReads = signReads;
    }

    /**
     * The posting with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no posting
     */
    public static BoundaryKind fromValue(String value) {
        for (BoundaryKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no boundary is stored as " + Repr.of(value));
    }

    /** The stored name, {@code high radiation area}. */
    public String value() {
        return value;
    }

    /** The dose rate at which the posting begins. */
    public DoseRate rate() {
        return rate;
    }

    /** What goes on the sign. */
    public String signReads() {
        return signReads;
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
