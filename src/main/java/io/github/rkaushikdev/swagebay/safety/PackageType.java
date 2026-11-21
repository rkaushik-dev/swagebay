package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;

/** The kind of radioactive material package a source travels in. */
public enum PackageType {
    EXCEPTED("excepted package"),
    TYPE_A("Type A package"),
    TYPE_B("Type B package");

    private final String value;

    PackageType(String value) {
        this.value = value;
    }

    /**
     * The package type with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no package type
     */
    public static PackageType fromValue(String value) {
        for (PackageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("no package type is stored as " + Repr.of(value));
    }

    /** The stored name, {@code Type B package}. */
    public String value() {
        return value;
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
