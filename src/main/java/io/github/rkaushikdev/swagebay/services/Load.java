package io.github.rkaushikdev.swagebay.services;

import java.util.Comparator;
import java.util.Objects;

/**
 * A camera and a source that could leave the shop together.
 *
 * @param deviceSerial the camera
 * @param sourceSerial the source in it
 */
public record Load(String deviceSerial, String sourceSerial) implements Comparable<Load> {

    private static final Comparator<Load> ORDER =
            Comparator.comparing(Load::deviceSerial).thenComparing(Load::sourceSerial);

    /** A load. */
    public Load {
        Objects.requireNonNull(deviceSerial, "deviceSerial");
        Objects.requireNonNull(sourceSerial, "sourceSerial");
    }

    /** By camera, then by source. */
    @Override
    public int compareTo(Load other) {
        return ORDER.compare(this, other);
    }
}
