package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;

/**
 * A sheet at the tube port that takes the soft end off the beam. The shot gets longer and the image
 * gets cleaner, and on thick sections it is worth it.
 *
 * @param material what the sheet is made of
 * @param thickness how thick it is
 * @param timeMultiplier how much longer the shot is with it fitted, at least one
 */
public record Filter(String material, Length thickness, BigDecimal timeMultiplier) {

    /**
     * A filter.
     *
     * @throws ValidationError for a thickness that is not positive or a multiplier under one
     */
    public Filter {
        if (thickness.um() <= 0) {
            throw new ValidationError("a filter has a thickness");
        }
        if (timeMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new ValidationError("a filter makes a shot longer, never shorter");
        }
    }

    /** {@code 0.50 mm copper, x1.4}. */
    public String describe() {
        return thickness.formatMm(2) + " " + material + ", x" + timeMultiplier;
    }
}
