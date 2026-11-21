package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Digital image files, their names, and proving none of them moved.
 *
 * <p>A shop that shoots plates or a panel ends up with a directory, and a directory is much easier
 * to lose quietly than a bag of film. So three rules. The name is derived, never typed: it comes
 * from the radiograph number, so a file with a name nobody can derive belongs to nothing and is
 * found on the first check. The record copy is lossless. And nothing is trusted without its digest,
 * which the {@link Manifest} takes at ingest.
 */
public final class ImageStore {

    /** The bit depth an evaluation is made at; a record copy may not be shallower. */
    public static final int EVALUATION_BIT_DEPTH = 12;

    /** A digest as the manifest stores it: sixty four lower case hex characters. */
    static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    /** A stored file name: the stem, an optional {@code -vN} revision, and the suffix. */
    static final Pattern NAME = Pattern.compile(
            "(?<stem>[A-Za-z0-9-]+?)(?:-v(?<revision>\\d+))?\\.(?<suffix>[a-z0-9]+)", Pattern.UNICODE_CHARACTER_CLASS);

    private static final String ALGORITHM = "SHA-256";

    private ImageStore() {}

    /** The hash the manifest stores, SHA-256 in lower case hex. One algorithm, named in one place. */
    public static String digestOf(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITHM).digest(data));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java platform provides " + ALGORITHM, missing);
        }
    }

    /** The name of the first file for an exposure. */
    public static String expectedName(String radiographNumber, ImageFormat imageFormat) {
        return expectedName(radiographNumber, imageFormat, 0);
    }

    /**
     * The only name a stored image is allowed to have: the radiograph number trimmed and in capitals,
     * a {@code -vN} revision when it is not the first, and the format's suffix.
     *
     * <p>A revision is a second file for the same exposure, which happens when a plate is rescanned
     * at a different pitch. It is a suffix rather than a new number, because the exposure did not
     * happen twice.
     *
     * @throws ValidationError for a blank number or a revision below zero
     */
    public static String expectedName(String radiographNumber, ImageFormat imageFormat, int revision) {
        if (Text.isBlank(radiographNumber)) {
            throw new ValidationError("a stored image belongs to a radiograph");
        }
        if (revision < 0) {
            throw new ValidationError(
                    "a revision counts up from zero", Context.of("value", Integer.toString(revision)));
        }
        String stem = Text.strip(radiographNumber).toUpperCase(Locale.ROOT);
        String tail = revision == 0 ? "" : "-v" + revision;
        return stem + tail + "." + imageFormat.suffix();
    }

    /** Everything wrong with one file, for what it says it is. */
    public static List<String> faults(StoredImage image) {
        List<String> found = new ArrayList<>();
        boolean record = image.purpose() == Purpose.RECORD;
        if (record && !image.imageFormat().isLossless()) {
            found.add(image.imageFormat() + " is lossy and cannot be the record copy");
        }
        if (record && image.bitDepth() < EVALUATION_BIT_DEPTH) {
            found.add(image.bitDepth() + " bit is below the " + EVALUATION_BIT_DEPTH + " bit an evaluation is made at");
        }
        if (image.purpose() == Purpose.VIEWING && image.revision() == 0) {
            found.add("a viewing copy is a revision of the record, not the record");
        }
        return List.copyOf(found);
    }

    /** Whether nothing is wrong with the file. */
    public static boolean isAdmissibleFile(StoredImage image) {
        return faults(image).isEmpty();
    }

    /** The revision a file name carries, zero when it carries none or is not a stored name at all. */
    static int revisionOf(String filename) {
        Matcher match = NAME.matcher(filename);
        if (!match.matches() || match.group("revision") == null) {
            return 0;
        }
        return Integer.parseInt(match.group("revision"));
    }
}
