package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;

/**
 * The three things a radiographer wears, which do three different jobs. The badge is the legal
 * record and is read months later by a laboratory. The direct reading dosimeter tells the crew what
 * happened this shift. The alarming ratemeter is the only one that can save anybody, because it
 * makes a noise before the dose is taken.
 */
public enum DosimeterKind {
    BADGE("personnel dosimeter"),
    DIRECT_READING("direct reading dosimeter"),
    ALARMING_RATEMETER("alarming ratemeter");

    private final String value;

    DosimeterKind(String value) {
        this.value = value;
    }

    /**
     * The kind with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no kind of dosimeter
     */
    public static DosimeterKind fromValue(String value) {
        for (DosimeterKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no dosimeter kind is stored as " + Repr.of(value));
    }

    /** The stored name, {@code alarming ratemeter}. */
    public String value() {
        return value;
    }

    /** Whether it can be read on the spot, which everything but the badge can. */
    public boolean readsOnTheDay() {
        return this != BADGE;
    }

    /** Whether it warns before the dose is taken rather than recording it afterwards. */
    public boolean warnsBeforeTheDose() {
        return this == ALARMING_RATEMETER;
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
