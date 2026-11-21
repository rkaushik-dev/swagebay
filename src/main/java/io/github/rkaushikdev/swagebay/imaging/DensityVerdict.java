package io.github.rkaushikdev.swagebay.imaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Whether a film passes on density, and every reason it does not.
 *
 * @param passed whether there is no reason against it
 * @param reasons every reason against it, in the order they were found
 * @param indicatorDensity the density through the indicator, when exactly one reading was taken there
 * @param low the bottom of the variation window, with the indicator density
 * @param high the top of the variation window, with the indicator density
 */
public record DensityVerdict(
        boolean passed,
        List<String> reasons,
        Optional<BigDecimal> indicatorDensity,
        Optional<BigDecimal> low,
        Optional<BigDecimal> high) {

    /** A verdict; the reasons are copied. */
    public DensityVerdict {
        reasons = List.copyOf(reasons);
    }

    /** {@code density acceptable}, or every reason joined with semicolons. */
    public String describe() {
        if (passed) {
            return "density acceptable";
        }
        return String.join("; ", reasons);
    }
}
