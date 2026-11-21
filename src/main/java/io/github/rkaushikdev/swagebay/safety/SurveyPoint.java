package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One reading, where it was taken and which way it faced.
 *
 * @param label what the point is called on the sketch, never blank
 * @param distance how far from the source, above zero
 * @param bearingDegrees the bearing off the beam axis
 * @param reading what the meter read
 * @param atDeviceSurface whether it was taken on the device, on the way past after the exposure
 */
public record SurveyPoint(
        String label, Length distance, BigDecimal bearingDegrees, DoseRate reading, boolean atDeviceSurface) {

    /**
     * A reading.
     *
     * @throws ValidationError without a label, or at no distance from the source
     */
    public SurveyPoint {
        Objects.requireNonNull(bearingDegrees, "bearingDegrees");
        Objects.requireNonNull(reading, "reading");
        if (Text.strip(label).isEmpty()) {
            throw new ValidationError("a survey point needs a label");
        }
        if (distance.um() <= 0) {
            throw new ValidationError("a survey point is somewhere", Context.of("label", label));
        }
    }

    /** A reading away from the device. */
    public SurveyPoint(String label, Length distance, BigDecimal bearingDegrees, DoseRate reading) {
        this(label, distance, bearingDegrees, reading, false);
    }
}
