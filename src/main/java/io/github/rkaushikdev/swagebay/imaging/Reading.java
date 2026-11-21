package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;

/**
 * One density reading: a spot on the film, and whether the indicator was under it.
 *
 * @param label where on the film the spot is
 * @param density the density read there
 * @param throughIndicator whether the spot is through the indicator rather than the weld
 */
public record Reading(String label, BigDecimal density, boolean throughIndicator) {

    private static final BigDecimal SCALE_END = Decimals.multiply(Films.DENSITY_CEILING, BigDecimal.valueOf(2));

    /**
     * A reading.
     *
     * @throws ValidationError for a blank label, a density that is not positive, or one off the
     *     instrument scale at twice the density ceiling
     */
    public Reading {
        if (Text.isBlank(label)) {
            throw new ValidationError("a density reading needs a location label");
        }
        if (density.signum() <= 0) {
            throw new ValidationError("a density reading has to be positive", Context.of("label", label));
        }
        if (density.compareTo(SCALE_END) > 0) {
            throw new ValidationError("that reading is off the instrument scale", Context.of("label", label));
        }
    }

    /** A reading through the weld rather than the indicator. */
    public Reading(String label, BigDecimal density) {
        this(label, density, false);
    }
}
