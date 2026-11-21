package io.github.rkaushikdev.swagebay.reporting;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The range of repair rates the shop normally sits in, from its own history.
 *
 * @param centre the pooled rate the band is set round
 * @param low the bottom of the band, included
 * @param high the top of the band, included
 */
public record Band(BigDecimal centre, BigDecimal low, BigDecimal high) {

    /** A band. */
    public Band {
        Objects.requireNonNull(centre, "centre");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
    }

    /** Whether the rate sits inside the band, both ends included. */
    public boolean contains(Rate rate) {
        BigDecimal value = rate.value();
        return low.compareTo(value) <= 0 && value.compareTo(high) <= 0;
    }

    /** {@code 4.00% band 2.00% to 6.00%}, the way the monthly note reads. */
    public String describe() {
        return Statistics.percent(centre) + " band " + Statistics.percent(low) + " to " + Statistics.percent(high);
    }
}
