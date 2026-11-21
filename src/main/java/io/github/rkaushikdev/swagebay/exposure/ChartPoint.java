package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;

/**
 * One reading off an exposure chart's curve.
 *
 * @param thickness the steel thickness along the bottom of the chart
 * @param curieMinutes the exposure up the side
 */
public record ChartPoint(Length thickness, BigDecimal curieMinutes) {

    /**
     * A reading.
     *
     * @throws ValidationError for a thickness or an exposure that is not positive
     */
    public ChartPoint {
        if (thickness.um() <= 0) {
            throw new ValidationError("a chart point needs a positive thickness");
        }
        if (curieMinutes.signum() <= 0) {
            throw new ValidationError("a chart point needs a positive exposure");
        }
    }
}
