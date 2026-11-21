package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;

/**
 * A whole exposure calculation, kept so the technique sheet can print every step of it.
 *
 * @param thickness the thickness the chart was read at
 * @param chartKey the chart that was read
 * @param chartReading curie minutes off the chart
 * @param corrections every correction applied to the reading
 * @param correctedReading the reading with the corrections applied
 * @param activity the activity of the source on the day
 * @param exposure the time on the timer
 * @param warnings why the shot is not practical, if it is not
 */
public record ExposureResult(
        Length thickness,
        String chartKey,
        BigDecimal chartReading,
        CorrectionChain corrections,
        BigDecimal correctedReading,
        Activity activity,
        Duration exposure,
        List<String> warnings) {

    /** A result; the warnings are copied. */
    public ExposureResult {
        warnings = List.copyOf(warnings);
    }

    /** A result with nothing to warn about. */
    public ExposureResult(
            Length thickness,
            String chartKey,
            BigDecimal chartReading,
            CorrectionChain corrections,
            BigDecimal correctedReading,
            Activity activity,
            Duration exposure) {
        this(thickness, chartKey, chartReading, corrections, correctedReading, activity, exposure, List.of());
    }

    /** What goes in the time box on the sheet. */
    public String timer() {
        return exposure.asTimer();
    }

    /** Whether nothing stands in the way of making the shot as planned. */
    public boolean isPractical() {
        return warnings.isEmpty();
    }

    /** The calculation as the technique sheet prints it. */
    public String describe() {
        return chartKey + " at " + thickness.formatMm() + " reads " + Decimals.normalize(chartReading) + " Ci min, "
                + corrections.describe() + ", " + activity.formatCurie() + " gives " + timer();
    }
}
