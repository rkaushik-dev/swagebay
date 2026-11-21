package io.github.rkaushikdev.swagebay.imaging;

/** Which hole in a hole type plaque has to be visible, as a multiple of the plaque thickness. */
public enum HoleSize {
    ONE_T(1),
    TWO_T(2),
    FOUR_T(4);

    private final int value;

    HoleSize(int value) {
        this.value = value;
    }

    /** The hole diameter as a multiple of the plaque thickness. */
    public int value() {
        return value;
    }

    /** The hole as the report names it: {@code 2T}. */
    public String label() {
        return value + "T";
    }

    @Override
    public String toString() {
        return label();
    }
}
