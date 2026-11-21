package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Processor control, which is the quietest way a shop goes wrong.
 *
 * <p>Nothing about a processor fails loudly. The developer gets tired, the temperature drifts a
 * degree, and every film comes out a little thinner than the one before. No single film is obviously
 * bad. Six weeks later the shop is shooting twenty percent longer than the technique says and nobody
 * knows why.
 *
 * <p>The limits are the written practice's and they are tighter than they look: speed may move by
 * fifteen hundredths of a density unit, contrast by twenty, fog by five. A strip outside any of them
 * stops the processor until somebody has looked at it, which is expensive and is the point.
 */
public final class Processing {

    /** How far the speed reading may move from the baseline. */
    public static final BigDecimal SPEED_TOLERANCE = new BigDecimal("0.15");

    /** How far the contrast reading may move from the baseline. */
    public static final BigDecimal CONTRAST_TOLERANCE = new BigDecimal("0.20");

    /** How far the fog reading may move from the baseline. */
    public static final BigDecimal FOG_TOLERANCE = new BigDecimal("0.05");

    private static final int DEFAULT_RUN = 5;

    private Processing() {}

    /** Every reading on one strip that is outside its band: speed, then contrast, then fog. */
    public static List<Excursion> excursions(Strip strip, Baseline baseline) {
        List<Excursion> out = new ArrayList<>();
        check(out, "speed", strip.speed(), baseline.speed(), SPEED_TOLERANCE);
        check(out, "contrast", strip.contrast(), baseline.contrast(), CONTRAST_TOLERANCE);
        check(out, "fog", strip.fog(), baseline.fog(), FOG_TOLERANCE);
        return List.copyOf(out);
    }

    /** Whether every reading on the strip is inside its band. */
    public static boolean inControl(Strip strip, Baseline baseline) {
        return excursions(strip, baseline).isEmpty();
    }

    /** The speed drift over a run of strips, oldest first. */
    public static List<BigDecimal> trend(List<Strip> strips, Baseline baseline) {
        return strips.stream()
                .sorted(Comparator.comparing(Strip::readOn))
                .map(strip -> Decimals.subtract(strip.speed(), baseline.speed()))
                .toList();
    }

    /** Whether the last five strips have all moved the same way. */
    public static boolean drifting(List<Strip> strips, Baseline baseline) {
        return drifting(strips, baseline, DEFAULT_RUN);
    }

    /**
     * Whether the last few strips have all moved the same way.
     *
     * <p>A run all on one side of the baseline is a drift even when every strip is inside its band,
     * and it is the thing the daily check exists to catch. One strip a hundredth low is noise. Five in
     * a row a hundredth low is a processor on its way out. Fewer strips than the run is no drift.
     *
     * @throws ValidationError for a run shorter than two strips
     */
    public static boolean drifting(List<Strip> strips, Baseline baseline, int run) {
        if (run < 2) {
            throw new ValidationError("a run is at least two strips", Context.of("run", run));
        }
        List<BigDecimal> drifts = trend(strips, baseline);
        if (drifts.size() < run) {
            return false;
        }
        List<BigDecimal> recent = drifts.subList(drifts.size() - run, drifts.size());
        return recent.stream().allMatch(d -> d.signum() > 0) || recent.stream().allMatch(d -> d.signum() < 0);
    }

    /** Whole days from the baseline to the strip. */
    public static long daysSinceBaseline(Strip strip, Baseline baseline) {
        return ChronoUnit.DAYS.between(baseline.establishedOn(), strip.readOn());
    }

    private static void check(
            List<Excursion> out, String reading, BigDecimal measured, BigDecimal base, BigDecimal tolerance) {
        if (Decimals.subtract(measured, base).abs().compareTo(tolerance) > 0) {
            out.add(new Excursion(reading, measured, base, tolerance));
        }
    }
}
