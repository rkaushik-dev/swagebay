package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * One factor applied to a chart reading, with what it was for.
 *
 * @param name what the factor corrects for, {@code distance} or {@code film}
 * @param factor what the reading is multiplied by
 * @param note the reason, as the technique sheet prints it
 */
public record Correction(String name, BigDecimal factor, String note) {

    /**
     * A correction.
     *
     * @throws ValidationError for a factor that is not positive
     */
    public Correction {
        if (factor.signum() <= 0) {
            throw new ValidationError(
                    "a correction factor has to be positive", Context.of("name", name, "factor", factor.toString()));
        }
    }

    /** The name and the factor with its trailing zeros gone: {@code screens x0.5}. */
    @Override
    public String toString() {
        return name + " x" + Decimals.normalize(factor);
    }
}
