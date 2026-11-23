package io.github.rkaushikdev.swagebay.materials;

import java.util.Arrays;
import java.util.Optional;

/**
 * A beam hard enough to be a column in the radiographic equivalence table.
 *
 * <p>The x-ray columns are read by the kilovoltage the set is run at: a shot at 120 kV reads the
 * 150 kV column, because a column covers everything above the one before it up to its own heading.
 * The isotope columns have no kilovoltage and are read by the isotope.
 */
public enum Quality {
    KV100("100 kV", 100),
    KV150("150 kV", 150),
    KV220("220 kV", 220),
    KV400("400 kV", 400),
    MV1("1 MV", 1_000),
    MV2("2 MV", 2_000),
    MV4_25("4 to 25 MV", 25_000),
    IR192("Ir-192", 0),
    CO60("Co-60", 0);

    private final String value;
    private final int ceilingKv;

    Quality(String value, int ceilingKv) {
        this.value = value;
        this.ceilingKv = ceilingKv;
    }

    /** The column an x-ray beam at that kilovoltage reads; nothing over 25 MV or for no voltage at all. */
    public static Optional<Quality> forKilovoltage(int kv) {
        if (kv <= 0) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(Quality::isXray)
                .filter(quality -> kv <= quality.ceilingKv)
                .findFirst();
    }

    /** Whether the column is for an x-ray beam rather than an isotope. */
    public boolean isXray() {
        return ceilingKv > 0;
    }

    /** The highest kilovoltage the column covers, or nothing for an isotope column. */
    public Optional<Integer> ceilingKv() {
        return isXray() ? Optional.of(ceilingKv) : Optional.empty();
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
