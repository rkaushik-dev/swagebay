package io.github.rkaushikdev.swagebay.materials;

/** A beam hard enough to be a column in the radiographic equivalence table. */
public enum Quality {
    KV100("100 kV"),
    KV150("150 kV"),
    KV220("220 kV"),
    KV400("400 kV"),
    MV1("1 MV"),
    MV2("2 MV"),
    MV4_25("4 to 25 MV"),
    IR192("Ir-192"),
    CO60("Co-60");

    private final String value;

    Quality(String value) {
        this.value = value;
    }

    /** The column heading, {@code 150 kV} or {@code Ir-192}. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
