package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Duplex wire gauges and the basic spatial resolution they measure.
 *
 * <p>The gauge measures the unsharpness of focal spot, geometry, detector and scanning at once
 * rather than calculating it a piece at a time. Resolution better than the wire indicator requires
 * is wasted, and resolution worse than it makes the wire indicator unreadable however good the
 * contrast. So a technique that fails on the wire and passes on the duplex gauge has a contrast
 * problem, and one that fails on both has a geometry problem.
 */
public final class Duplex {

    /** Pair number to wire diameter, which is also the gap. */
    static final Map<Integer, Length> WIRE_DIAMETERS = wireDiameters();

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private Duplex() {}

    /** Every pair on the gauge, coarsest first. */
    public static List<DuplexPair> pairs() {
        return WIRE_DIAMETERS.keySet().stream().sorted().map(DuplexPair::new).toList();
    }

    /**
     * The reader names a pair number; this is that pair.
     *
     * @throws ConfigurationError for a number the gauge does not have
     */
    public static DuplexPair finestResolved(int number) {
        return new DuplexPair(number);
    }

    /**
     * The basic spatial resolution a reading of that pair number means.
     *
     * @throws ConfigurationError for a number the gauge does not have
     */
    public static Length resolutionFromReading(int number) {
        return new DuplexPair(number).basicSpatialResolution();
    }

    /**
     * The coarsest pair that still meets a required resolution, which is the one to ask for.
     *
     * @throws ValidationError for a target that is not positive
     * @throws ConfigurationError when no pair on the gauge is fine enough
     */
    public static DuplexPair pairForResolution(Length target) {
        if (target.um() <= 0) {
            throw new ValidationError("a resolution target has to be positive");
        }
        return pairs().stream()
                .filter(pair -> pair.basicSpatialResolution().compareTo(target) <= 0)
                .findFirst()
                .orElseThrow(() -> new ConfigurationError(
                        "no duplex pair is fine enough for that target",
                        Context.of("target_mm", target.mm().toString())));
    }

    /** Whether a reading of that pair meets the required resolution. A comparison, not a count. */
    public static boolean meets(int reading, Length required) {
        return resolutionFromReading(reading).compareTo(required) <= 0;
    }

    /**
     * The geometric and detector unsharpness added in quadrature, truncated to the micrometre.
     *
     * <p>Adding them straight overstates the total and has retired techniques that would have
     * worked. The quadrature sum is what the standards use and what a duplex gauge actually measures.
     *
     * @throws ValidationError for a term below zero
     */
    public static Length unsharpnessBudget(Length geometric, Length detector) {
        if (geometric.um() < 0 || detector.um() < 0) {
            throw new ValidationError("an unsharpness term cannot be negative");
        }
        BigDecimal total = Decimals.add(
                Decimals.power(BigDecimal.valueOf(geometric.um()), TWO),
                Decimals.power(BigDecimal.valueOf(detector.um()), TWO));
        return new Length(Decimals.truncate(Decimals.sqrt(total)));
    }

    private static Map<Integer, Length> wireDiameters() {
        String[] millimetres = {
            "0.80", "0.63", "0.50", "0.40", "0.32", "0.25", "0.20", "0.16", "0.13", "0.10", "0.08", "0.063", "0.05"
        };
        Map<Integer, Length> diameters = new LinkedHashMap<>();
        for (int i = 0; i < millimetres.length; i++) {
            diameters.put(i + 1, Length.fromMm(millimetres[i]));
        }
        return Collections.unmodifiableMap(diameters);
    }
}
