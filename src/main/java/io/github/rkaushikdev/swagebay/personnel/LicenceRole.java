package io.github.rkaushikdev.swagebay.personnel;

import io.github.rkaushikdev.swagebay.util.Repr;

/** What the radiation licence lets somebody do with a source. */
public enum LicenceRole {
    NONE("not licensed"),
    ASSISTANT("radiographer assistant"),
    RADIOGRAPHER("radiographer"),
    SAFETY_OFFICER("radiation safety officer");

    private final String value;

    LicenceRole(String value) {
        this.value = value;
    }

    /**
     * The role with that stored phrase.
     *
     * @throws IllegalArgumentException for a phrase that names no role
     */
    public static LicenceRole fromValue(String value) {
        for (LicenceRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("no licence role is stored as " + Repr.of(value));
    }

    /** The stored phrase, {@code radiographer assistant}. */
    public String value() {
        return value;
    }

    /** Whether the licence lets this role run a job without a radiographer at their side. */
    public boolean mayOperateUnattended() {
        return this == RADIOGRAPHER || this == SAFETY_OFFICER;
    }

    /** The stored phrase. */
    @Override
    public String toString() {
        return value;
    }
}
