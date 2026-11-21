package io.github.rkaushikdev.swagebay.imaging;

/**
 * Something somebody did to a digital image.
 *
 * <p>The line the codes draw is between operations that are reversible and operations that are
 * not. Windowing and contrast stretch change what is on the screen and leave the stored data
 * alone, so anybody may use them. Sharpening, smoothing and noise reduction throw information away
 * and can create an edge that was not there, so they are allowed only if they were qualified as
 * part of the technique. A local edit is alteration, not enhancement, and a lossy compression
 * destroys exactly the small low contrast detail the radiograph exists to show; neither is ever
 * allowed on a graded image.
 */
public enum Operation {
    WINDOW("window and level"),
    CONTRAST_STRETCH("contrast stretch"),
    ROTATE_90("rotate by a right angle"),
    MIRROR("mirror"),
    SHARPEN("unsharp mask"),
    SMOOTH("smoothing"),
    NOISE_REDUCTION("noise reduction"),
    LOCAL_EDIT("local edit"),
    LOSSY_COMPRESSION("lossy compression");

    private final String value;

    Operation(String value) {
        this.value = value;
    }

    /** The operation as the history writes it. */
    public String value() {
        return value;
    }

    /** Whether the stored data survives it. */
    public boolean isReversible() {
        return switch (this) {
            case WINDOW, CONTRAST_STRETCH, ROTATE_90, MIRROR -> true;
            default -> false;
        };
    }

    /** Whether it is allowed only as part of a qualified technique. */
    public boolean needsQualification() {
        return switch (this) {
            case SHARPEN, SMOOTH, NOISE_REDUCTION -> true;
            default -> false;
        };
    }

    /** Whether it is never allowed on a graded image. */
    public boolean isForbidden() {
        return this == LOCAL_EDIT || this == LOSSY_COMPRESSION;
    }

    @Override
    public String toString() {
        return value;
    }
}
