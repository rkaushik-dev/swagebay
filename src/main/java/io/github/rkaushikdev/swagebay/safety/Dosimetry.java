package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import java.math.BigDecimal;

/**
 * The dose limits and the investigation levels under them.
 *
 * <p>The investigation levels are the useful part. Nobody in this trade gets near the legal limit,
 * so the limit never fires and never tells anybody anything. The level at a tenth of it fires two
 * or three times a year, and every time it does somebody finds out why.
 */
public final class Dosimetry {

    /** The annual limit for an adult worker. */
    public static final Dose ANNUAL_LIMIT = Dose.fromMsv(50);

    /** The limit for a declared pregnant worker, a tenth of the adult one. */
    public static final Dose DECLARED_PREGNANT_LIMIT = Dose.fromMsv(5);

    /** The limit for a minor. */
    public static final Dose MINOR_LIMIT = Dose.fromMsv(5);

    /** The share of the limit at which the first investigation level fires. */
    public static final BigDecimal INVESTIGATION_LEVEL_ONE = new BigDecimal("0.10");

    /** The share of the limit at which the second investigation level fires. */
    public static final BigDecimal INVESTIGATION_LEVEL_TWO = new BigDecimal("0.30");

    /** The annual limit on the lens of the eye for an adult worker. */
    public static final Dose LENS_LIMIT = Dose.fromMsv(150);

    /** The annual limit on the skin and the extremities for an adult worker. */
    public static final Dose SHALLOW_LIMIT = Dose.fromMsv(500);

    /** The share of the adult limits a minor is held to, on every quantity. */
    public static final BigDecimal MINOR_FRACTION = new BigDecimal("0.1");

    private Dosimetry() {}

    /** Zero, one or two for a share of a limit, which is what the monthly report is keyed on. */
    public static int investigationLevel(BigDecimal fraction) {
        if (fraction.compareTo(INVESTIGATION_LEVEL_TWO) >= 0) {
            return 2;
        }
        if (fraction.compareTo(INVESTIGATION_LEVEL_ONE) >= 0) {
            return 1;
        }
        return 0;
    }
}
