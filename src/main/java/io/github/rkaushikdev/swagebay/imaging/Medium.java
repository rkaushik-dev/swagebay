package io.github.rkaushikdev.swagebay.imaging;

/**
 * What a radiograph is recorded on, which decides the gate it is graded by.
 *
 * <p>Film is graded on density, and the density rule is meaningless on a digital image, whose grey
 * values can be windowed to anything. A plate or a panel is graded on normalised signal to noise,
 * which cannot be measured on film at all.
 */
public enum Medium {
    FILM("film"),
    PLATE("computed radiography"),
    PANEL("digital detector array");

    private final String value;

    Medium(String value) {
        this.value = value;
    }

    /** The medium as the record names it. */
    public String value() {
        return value;
    }

    /** Whether the density gate runs on this medium. */
    public boolean gradedOnDensity() {
        return this == FILM;
    }

    /** Whether the noise gate runs on this medium. */
    public boolean gradedOnNoise() {
        return this != FILM;
    }

    /** Whether the image is numbers rather than a physical film. */
    public boolean isDigital() {
        return this != FILM;
    }

    @Override
    public String toString() {
        return value;
    }
}
