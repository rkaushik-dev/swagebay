package io.github.rkaushikdev.swagebay.safety;

/**
 * The three doses a badge reports and the regulation limits separately.
 *
 * <p>A badge has filters in it for a reason. The deep dose is the whole body, and it is the one
 * everybody watches. The lens of the eye has its own limit three times higher, and the skin and the
 * extremities one ten times higher. A radiographer who handles a guide tube that has a source stuck
 * in the end of it can take a hand dose far past anything the deep reading shows, and the only place
 * that shows up is the shallow filter.
 */
public enum DoseQuantity {
    DEEP("deep dose"),
    LENS("lens of the eye"),
    SHALLOW("skin and extremities");

    private final String value;

    DoseQuantity(String value) {
        this.value = value;
    }

    /** The quantity as the dosimetry report heads its column. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
