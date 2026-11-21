package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * A repair rate with the count it came from, which is the only honest shape.
 *
 * <p>Four percent on a thousand welds is a shop running well. Four percent on twenty five welds is
 * one repair, which is noise. So the rate carries its denominator, and below {@link
 * Statistics#MINIMUM_DENOMINATOR} it is not reported as a rate at all.
 *
 * @param repaired the welds repaired
 * @param shot the welds shot
 */
public record Rate(int repaired, int shot) {

    /**
     * A rate.
     *
     * @throws ValidationError for a negative count, or more welds repaired than shot
     */
    public Rate {
        if (shot < 0 || repaired < 0) {
            throw new ValidationError("counts are not negative");
        }
        if (repaired > shot) {
            throw new ValidationError("more welds repaired than shot", Context.of("repaired", repaired, "shot", shot));
        }
    }

    /** Whether enough welds were shot for the rate to mean anything. */
    public boolean isReportable() {
        return shot >= Statistics.MINIMUM_DENOMINATOR;
    }

    /** Repaired over shot, and zero for a shop with no welds rather than a crash. */
    public BigDecimal value() {
        if (shot == 0) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(repaired, shot);
    }

    /** {@code 4.00% on 100}, or {@code 1 of 19, too few to rate} below the minimum denominator. */
    public String describe() {
        if (!isReportable()) {
            return repaired + " of " + shot + ", too few to rate";
        }
        return Statistics.percent(value()) + " on " + shot;
    }
}
