package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A thickness of one shielding material.
 *
 * @param material the material, a key of the half value layer table such as {@code lead}
 * @param thickness how much of it, never negative
 */
public record Barrier(String material, Length thickness) {

    /**
     * A barrier.
     *
     * @throws ValidationError for a negative thickness
     */
    public Barrier {
        Objects.requireNonNull(material, "material");
        if (thickness.um() < 0) {
            throw new ValidationError("a barrier cannot be a negative thickness");
        }
    }

    /**
     * The fraction of the rate from that isotope that gets through.
     *
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError when no half value layer is published
     *     for the material and isotope
     */
    public BigDecimal transmission(Isotope source) {
        return Shielding.transmission(source, material, thickness);
    }

    /**
     * How many half value layers of that isotope the barrier is.
     *
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError when no half value layer is published
     *     for the material and isotope
     */
    public BigDecimal layers(Isotope source) {
        return thickness.ratio(Shielding.halfValueLayer(source, material));
    }

    /** The barrier as a survey note reads: {@code 4.80 mm of lead}. */
    @Override
    public String toString() {
        return thickness.formatMm() + " of " + material;
    }
}
