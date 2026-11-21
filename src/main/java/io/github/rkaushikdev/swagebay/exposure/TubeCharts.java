package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.sources.Tube;
import io.github.rkaushikdev.swagebay.sources.Tubes;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * Turning a tube chart reading into a current and a time the set can hold.
 *
 * <p>A tube chart reading needs no decaying, because a tube does not get weaker. What it needs is
 * splitting into a current and a time, and the split is bounded at both ends: the set will not give
 * more than its rated current, and a time under a second is not reproducible on a mechanical timer.
 */
public final class TubeCharts {

    /**
     * An exposure split into what is set on the control panel.
     *
     * @param current the tube current, in milliamperes
     * @param time the time on the timer, a whole number of seconds
     */
    public record Split(BigDecimal current, Duration time) {}

    private TubeCharts() {}

    /** {@link #split(BigDecimal, Tube, BigDecimal)} at the set's full rated current. */
    public static Split split(BigDecimal mas, Tube tube) {
        return split(mas, tube, tube.maxMa());
    }

    /**
     * Split an exposure into that current and the time it takes, up to the next whole second.
     *
     * @throws ValidationError for an exposure or a current that is not positive
     * @throws TechniqueError for a current over the set's rating, or a time under a second
     */
    public static Split split(BigDecimal mas, Tube tube, BigDecimal preferredMa) {
        if (mas.signum() <= 0) {
            throw new ValidationError("an exposure has to be positive");
        }
        tube.checkCurrent(preferredMa);
        BigDecimal rawSeconds = Decimals.divide(mas, preferredMa);
        if (rawSeconds.compareTo(BigDecimal.ONE) < 0) {
            throw new TechniqueError(
                    "that exposure is under a second, drop the current or the kilovoltage",
                    Context.of("mas", mas.toString(), "ma", preferredMa.toString(), "seconds", rawSeconds.toString()));
        }
        return new Split(preferredMa, Tubes.timeFor(preferredMa, mas));
    }
}
