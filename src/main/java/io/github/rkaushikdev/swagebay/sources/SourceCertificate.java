package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;

/**
 * What came in the box with a sealed source.
 *
 * <p>A serial number, the isotope, how many curies it held at an assay moment, and the size of the
 * active pellet. The last is the number nobody expects to need, and it matters more than the
 * curies: the pellet is the focal spot, and the focal spot decides how sharp the image can be.
 *
 * @param serial the serial number stamped on the capsule
 * @param isotope what the pellet is made of
 * @param assayActivity the activity at the assay
 * @param assayAt when the activity was measured
 * @param manufacturer who made the source
 * @param model the capsule model
 * @param activeDiameter the diameter of the active pellet
 * @param activeLength the length of the active pellet
 */
public record SourceCertificate(
        String serial,
        Isotope isotope,
        Activity assayActivity,
        LocalDateTime assayAt,
        String manufacturer,
        String model,
        Length activeDiameter,
        Length activeLength) {

    /**
     * A certificate.
     *
     * @throws ValidationError without a serial, a positive assay activity, or a pellet of some size
     */
    public SourceCertificate {
        if (Text.strip(serial).isEmpty()) {
            throw new ValidationError("a source certificate needs a serial number");
        }
        if (assayActivity.bq() <= 0) {
            throw new ValidationError("an assay activity has to be positive", Context.of("serial", serial));
        }
        requireSize(serial, "active_diameter", activeDiameter);
        requireSize(serial, "active_length", activeLength);
    }

    /**
     * The dimension that goes into the unsharpness formula.
     *
     * <p>The pellet is a cylinder and it projects as a rectangle. The larger of the two dimensions is
     * the conservative reading and it is what the procedure says to use, because the crew does not
     * know which way the pigtail landed in the guide tube.
     */
    public Length focalSize() {
        return activeDiameter.compareTo(activeLength) >= 0 ? activeDiameter : activeLength;
    }

    private static void requireSize(String serial, String field, Length value) {
        if (value.um() <= 0) {
            throw new ValidationError(
                    "an active pellet has to have a size", Context.of("serial", serial, "field", field));
        }
    }
}
