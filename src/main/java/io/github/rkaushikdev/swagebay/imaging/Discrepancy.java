package io.github.rkaushikdev.swagebay.imaging;

import java.util.ArrayList;
import java.util.List;

/**
 * What verifying the store against its manifest found, kept apart by cause rather than counted.
 *
 * <p>A file that is missing, a file nobody expected, and a file whose contents are not what they
 * were have three different causes, and lumping them into one count sends everybody looking in the
 * wrong place.
 *
 * @param missing files the manifest holds that are not on the disk, sorted
 * @param unexpected files on the disk the manifest does not hold, sorted
 * @param altered files whose bytes no longer hash to what they did at ingest, sorted
 */
public record Discrepancy(List<String> missing, List<String> unexpected, List<String> altered) {

    /** A discrepancy; the lists are copied. */
    public Discrepancy {
        missing = List.copyOf(missing);
        unexpected = List.copyOf(unexpected);
        altered = List.copyOf(altered);
    }

    /** Whether every file is where it was and is what it was. */
    public boolean isClean() {
        return missing.isEmpty() && unexpected.isEmpty() && altered.isEmpty();
    }

    /** {@code 1 missing, 1 not in the manifest, 1 changed since ingest}, each cause only when present. */
    public String describe() {
        if (isClean()) {
            return "every file is where it was and is what it was";
        }
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add(missing.size() + " missing");
        }
        if (!unexpected.isEmpty()) {
            parts.add(unexpected.size() + " not in the manifest");
        }
        if (!altered.isEmpty()) {
            parts.add(altered.size() + " changed since ingest");
        }
        return String.join(", ", parts);
    }
}
