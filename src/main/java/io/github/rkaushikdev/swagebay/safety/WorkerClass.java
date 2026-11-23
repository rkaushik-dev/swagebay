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

    /** The annual limit for this class on the deep dose. */
    public Dose limit() {
        return limit(DoseQuantity.DEEP);
    }

    /**
     * The annual limit for this class on that quantity. A minor is held to a tenth of every adult
     * limit. The declared pregnant limit is the embryo's and only the deep dose reaches it, so the
     * worker's own lens and skin limits stay the adult ones.
     */
    public Dose limit(DoseQuantity quantity) {
        Dose adult =
                switch (quantity) {
                    case DEEP -> Dosimetry.ANNUAL_LIMIT;
                    case LENS -> Dosimetry.LENS_LIMIT;
                    case SHALLOW -> Dosimetry.SHALLOW_LIMIT;
                };
        return switch (this) {
            case ADULT -> adult;
            case MINOR -> quantity == DoseQuantity.DEEP ? Dosimetry.MINOR_LIMIT : adult.times(Dosimetry.MINOR_FRACTION);
            case DECLARED_PREGNANT -> quantity == DoseQuantity.DEEP ? Dosimetry.DECLARED_PREGNANT_LIMIT : adult;
        };
    }

    /** The stored name. */
    @Override
    public String toString() {
        return value;
    }
}
