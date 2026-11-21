package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.util.Arrays;
import java.util.Locale;

/**
 * What the bytes of a stored image are, and whether they can be the record.
 *
 * <p>The record copy is lossless. A jpeg of a weld is a picture of a radiograph, not a radiograph,
 * and the difference matters at the point where somebody is trying to decide whether a faint line
 * is a lack of fusion. Lossy files are allowed in the store as viewing copies and are never the
 * evidence.
 */
public enum ImageFormat {
    DICONDE("dcm"),
    TIFF("tif"),
    PNG("png"),
    JPEG("jpg");

    private final String value;

    ImageFormat(String value) {
        this.value = value;
    }

    /**
     * The format whose stored value, its file suffix, is that.
     *
     * @throws IllegalArgumentException for a suffix that names no format
     */
    public static ImageFormat fromValue(String value) {
        return Arrays.stream(values())
                .filter(format -> format.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no image format is stored as " + Repr.of(value)));
    }

    /** The stored value, which is the file suffix. */
    public String value() {
        return value;
    }

    /** The file suffix, without the dot. */
    public String suffix() {
        return value;
    }

    /** Whether the format keeps every pixel as it was. */
    public boolean isLossless() {
        return this != JPEG;
    }

    /** Only DICONDE has somewhere to put the technique with the pixels. */
    public boolean carriesTechnique() {
        return this == DICONDE;
    }

    /** The format's name in lower case: {@code diconde}. */
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
