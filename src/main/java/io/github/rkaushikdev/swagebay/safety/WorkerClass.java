package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.util.Repr;

/** Which annual limit a worker is held to. */
public enum WorkerClass {
    ADULT("adult worker"),
    DECLARED_PREGNANT("declared pregnant worker"),
    MINOR("minor");

    private final String value;

    WorkerClass(String value) {
        this.value = value;
    }

    /**
     * The class with that stored name.
     *
     * @throws IllegalArgumentException for a name that is no class of worker
     */
    public static WorkerClass fromValue(String value) {
        for (WorkerClass worker : values()) {
            if (worker.value.equals(value)) {
                return worker;
            }
        }
        throw new IllegalArgumentException("no worker class is stored as " + Repr.of(value));
    }

    /** The stored name, {@code declared pregnant worker}. */
    public String value() {
        return value;
    }

    /** The annual limit for this class. */
    public Dose limit() {
        return switch (this) {
            case DECLARED_PREGNANT -> Dosimetry.DECLARED_PREGNANT_LIMIT;
            case MINOR -> Dosimetry.MINOR_LIMIT;
            case ADULT -> Dosimetry.ANNUAL_LIMIT;
        };
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
