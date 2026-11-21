package io.github.rkaushikdev.swagebay.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything standing between a package and the post.
 *
 * @param packageFaults what is wrong with the package itself
 * @param missingEnclosures what the client asked for that is not in the parcel
 */
public record Readiness(List<String> packageFaults, List<Enclosure> missingEnclosures) {

    /** A readiness; the lists are copied. */
    public Readiness {
        packageFaults = List.copyOf(packageFaults);
        missingEnclosures = List.copyOf(missingEnclosures);
    }

    /** Whether the package is sound and the parcel holds everything asked for. */
    public boolean isReady() {
        return packageFaults.isEmpty() && missingEnclosures.isEmpty();
    }

    /**
     * {@code ready to go}, or every package fault and then every missing enclosure, {@code no the
     * procedure revision enclosed}, joined with semicolons.
     */
    public String describe() {
        if (isReady()) {
            return "ready to go";
        }
        List<String> parts = new ArrayList<>(packageFaults);
        missingEnclosures.forEach(item -> parts.add("no " + item + " enclosed"));
        return String.join("; ", parts);
    }
}
