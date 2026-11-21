package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * One file in the image store, and everything the manifest needs about it.
 *
 * @param radiographNumber the radiograph the file belongs to
 * @param filename the file's name, which has to be the one {@link ImageStore#expectedName} derives
 * @param imageFormat what the bytes are
 * @param bitDepth bits per pixel: 8, 12, 14 or 16
 * @param width the width in pixels
 * @param height the height in pixels
 * @param byteSize the file size in bytes
 * @param digest the SHA-256 of the bytes taken at ingest, in lower case hex
 * @param capturedAt when the image was captured
 * @param purpose whether it is the record or a viewing copy
 */
public record StoredImage(
        String radiographNumber,
        String filename,
        ImageFormat imageFormat,
        int bitDepth,
        int width,
        int height,
        long byteSize,
        String digest,
        LocalDateTime capturedAt,
        Purpose purpose) {

    private static final Set<Integer> BIT_DEPTHS = Set.of(8, 12, 14, 16);

    /**
     * A stored image.
     *
     * @throws ValidationError for a digest that is not one, a bit depth nobody ships, a missing
     *     dimension, an empty file, or a name that cannot be derived from the radiograph it claims
     */
    public StoredImage {
        if (!ImageStore.DIGEST.matcher(digest).matches()) {
            throw new ValidationError("a digest is sixty four hex characters", Context.of("value", digest));
        }
        if (!BIT_DEPTHS.contains(bitDepth)) {
            throw new ValidationError("that is not a bit depth", Context.of("value", Integer.toString(bitDepth)));
        }
        if (width <= 0 || height <= 0) {
            throw new ValidationError("an image has both dimensions");
        }
        if (byteSize <= 0) {
            throw new ValidationError("an empty file is not an image");
        }
        if (!filename.equals(ImageStore.expectedName(radiographNumber, imageFormat, ImageStore.revisionOf(filename)))) {
            throw new ValidationError(
                    "that name cannot be derived from the radiograph it claims",
                    Context.of("filename", filename, "number", radiographNumber));
        }
    }

    /** The record copy of an exposure. */
    public StoredImage(
            String radiographNumber,
            String filename,
            ImageFormat imageFormat,
            int bitDepth,
            int width,
            int height,
            long byteSize,
            String digest,
            LocalDateTime capturedAt) {
        this(
                radiographNumber,
                filename,
                imageFormat,
                bitDepth,
                width,
                height,
                byteSize,
                digest,
                capturedAt,
                Purpose.RECORD);
    }

    /** The revision the name carries; zero for the first file of an exposure. */
    public int revision() {
        return ImageStore.revisionOf(filename);
    }

    /** The pixel count. */
    public long pixels() {
        return (long) width * height;
    }

    /** {@code 24-0001-R0001.dcm 2048x512 16 bit 23 bytes record}. */
    public String describe() {
        return filename + " " + width + "x" + height + " " + bitDepth + " bit " + byteSize + " bytes " + purpose;
    }
}
