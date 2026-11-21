package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turning a chart reading into a number of seconds on a timer.
 *
 * <p>The last step is a division: the corrected curie minutes over the curies in the pig. What
 * makes it worth writing down is the two bounds either side of it. A shot under about ten seconds
 * is not a shot, because the source spends a few seconds travelling out the guide tube and a few
 * coming back, exposing the film from the wrong place, and below ten seconds the density is not
 * reproducible. A shot longer than the window the client gives is not a shot either, and finding
 * that out on site rather than in the office costs a night. A Level III can override either bound,
 * so both are warnings, and neither can be left out of the record.
 */
public final class Calculation {

    /** Below this the transit of the source is a big enough fraction of the shot to show. */
    public static final Duration MINIMUM_PRACTICAL = Duration.fromSeconds(10);

    private Calculation() {}

    /**
     * The division on its own, up to the next whole second, for a caller that already has the
     * numbers.
     *
     * @throws ValidationError for a reading that is not positive
     * @throws TechniqueError for an empty source
     */
    public static Duration exposureTime(BigDecimal correctedCurieMinutes, Activity activity) {
        if (correctedCurieMinutes.signum() <= 0) {
            throw new ValidationError("a corrected reading has to be positive");
        }
        if (activity.bq() <= 0) {
            throw new TechniqueError("an empty source cannot make an exposure");
        }
        BigDecimal minutes = Decimals.divide(correctedCurieMinutes, activity.curie());
        return Duration.fromMinutes(minutes).roundUpToSecond();
    }

    /** {@link #planExposure(ExposureChart, Length, CorrectionChain, Activity, Optional)} with no window to fit. */
    public static ExposureResult planExposure(
            ExposureChart chart, Length thickness, CorrectionChain corrections, Activity activity) {
        return planExposure(chart, thickness, corrections, activity, Optional.empty());
    }

    /** {@link #planExposure(ExposureChart, Length, CorrectionChain, Activity, Optional)} within a window. */
    public static ExposureResult planExposure(
            ExposureChart chart, Length thickness, CorrectionChain corrections, Activity activity, Duration window) {
        return planExposure(chart, thickness, corrections, activity, Optional.of(window));
    }

    /**
     * Read the chart, apply the chain, divide by the curies, and check the bounds.
     *
     * @param window the clear time the client gives, when there is a limit
     * @throws TechniqueError for a thickness off the chart, before any division, or an empty source
     */
    public static ExposureResult planExposure(
            ExposureChart chart,
            Length thickness,
            CorrectionChain corrections,
            Activity activity,
            Optional<Duration> window) {
        BigDecimal reading = chart.factorFor(thickness);
        BigDecimal corrected = corrections.appliedTo(reading);
        Duration span = exposureTime(corrected, activity);

        List<String> warnings = new ArrayList<>();
        if (span.compareTo(MINIMUM_PRACTICAL) < 0) {
            warnings.add("exposure of " + span.asTimer() + " is under the ten second floor, "
                    + "use a slower film or a longer distance");
        }
        if (window.isPresent() && span.compareTo(window.get()) > 0) {
            warnings.add("exposure of " + span.asTimer() + " does not fit the "
                    + window.get().asTimer() + " window");
        }
        return new ExposureResult(thickness, chart.key(), reading, corrections, corrected, activity, span, warnings);
    }

    /**
     * How strong a source would make that shot in that time.
     *
     * @throws ValidationError for a target time or a reading that is not positive
     */
    public static Activity activityNeeded(BigDecimal correctedCurieMinutes, Duration target) {
        if (target.ms() <= 0) {
            throw new ValidationError("a target exposure has to be positive");
        }
        if (correctedCurieMinutes.signum() <= 0) {
            throw new ValidationError("a corrected reading has to be positive");
        }
        return Activity.fromCurie(Decimals.divide(correctedCurieMinutes, target.minutes()));
    }

    /**
     * How long the source is out over the whole joint, which is what the safety side wants.
     *
     * @throws ValidationError for fewer than one exposure
     */
    public static Duration totalForExposures(Duration single, int count) {
        if (count <= 0) {
            throw new ValidationError("a joint needs at least one exposure", Context.of("count", count));
        }
        return new Duration(Math.multiplyExact(single.ms(), count));
    }
}
