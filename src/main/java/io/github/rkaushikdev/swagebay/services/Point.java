package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Objects;

/**
 * One survey reading, as the surveyor took it.
 *
 * @param label where it was taken, in the surveyor's words
 * @param distance how far from the source, above zero
 * @param bearingDegrees which way from the source
 * @param reading what the meter read
 * @param atDeviceSurface whether it was taken against the camera, which is the reading that says
 *     whether the source went home
 */
public record Point(String label, Length distance, int bearingDegrees, DoseRate reading, boolean atDeviceSurface) {

    /**
     * A reading.
     *
     * @throws ValidationError for a reading with no label, or one taken at no distance
     */
    public Point {
        Objects.requireNonNull(distance, "distance");
        Objects.requireNonNull(reading, "reading");
        if (Text.isBlank(label)) {
            throw new ValidationError("a survey point has a label");
        }
        if (distance.um() <= 0) {
            throw new ValidationError("a survey point is somewhere", Context.of("label", label));
        }
    }

    /** A reading taken away from the device. */
    public Point(String label, Length distance, int bearingDegrees, DoseRate reading) {
        this(label, distance, bearingDegrees, reading, false);
    }
}
