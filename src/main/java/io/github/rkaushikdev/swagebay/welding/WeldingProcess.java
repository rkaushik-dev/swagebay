package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.util.Repr;

/** A welding process a welder qualifies on, one qualification per process. */
public enum WeldingProcess {
    SMAW("shielded metal arc"),
    GTAW("gas tungsten arc"),
    GMAW("gas metal arc"),
    FCAW("flux cored arc");

    private final String value;

    WeldingProcess(String value) {
        this.value = value;
    }

    /**
     * The process with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no process
     */
    public static WeldingProcess fromValue(String value) {
        for (WeldingProcess process : values()) {
            if (process.value.equals(value)) {
                return process;
            }
        }
        throw new IllegalArgumentException("no welding process is stored as " + Repr.of(value));
    }

    /** The stored name, {@code shielded metal arc}. */
    public String value() {
        return value;
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
