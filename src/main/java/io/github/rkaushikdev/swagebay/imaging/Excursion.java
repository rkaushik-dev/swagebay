package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * One strip reading outside its band.
 *
 * @param reading which reading: speed, contrast or fog
 * @param measured what the strip read
 * @param baseline what the processor read the day it was set up
 * @param tolerance how far it may move
 */
public record Excursion(String reading, BigDecimal measured, BigDecimal baseline, BigDecimal tolerance) {

    /** How far the reading has moved from the baseline, up positive. */
    public BigDecimal drift() {
        return Decimals.subtract(measured, baseline);
    }

    /** {@code high} or {@code low}. */
    public String direction() {
        return drift().signum() > 0 ? "high" : "low";
    }

    /** {@code speed is 0.20 low of the baseline, over the 0.15 allowed}. */
    public String describe() {
        return reading + " is " + drift().abs() + " " + direction() + " of the baseline, over the " + tolerance
                + " allowed";
    }
}
