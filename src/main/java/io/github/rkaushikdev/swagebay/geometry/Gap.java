package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * A stretch of a circumference that the walk of a weld map picked out: one nothing covers, or one
 * only a single film reaches.
 *
 * @param startsAt the start, in degrees clockwise from twelve
 * @param span how far round it runs
 */
public record Gap(BigDecimal startsAt, BigDecimal span) {

    /** The stretch as a report reads it, such as 320.0&deg; to 20.0&deg;. */
    public String describe() {
        return startsAt + WeldMap.DEGREE + " to " + WeldMap.wrap(Decimals.add(startsAt, span)) + WeldMap.DEGREE;
    }
}
