package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * How many exposures a girth weld takes, how far apart, and how much each really carries.
 *
 * @param exposures the exposure count, never below the code minimum
 * @param usableHalfArcDegrees the arc either side of the beam axis that stays inside the path allowance
 * @param separationDegrees how far apart round the pipe the exposures are
 * @param overlapDegrees how much each exposure's arc runs past the next one's start
 */
public record CoveragePlan(
        int exposures, BigDecimal usableHalfArcDegrees, BigDecimal separationDegrees, BigDecimal overlapDegrees) {

    /** Whether neighbouring exposures overlap. */
    public boolean hasOverlap() {
        return overlapDegrees.signum() > 0;
    }

    /** The plan as the sheet reads: {@code 5 exposures 72.0 degrees apart, each carrying 83.5 degrees}. */
    public String describe() {
        BigDecimal carried = Decimals.multiply(usableHalfArcDegrees, BigDecimal.valueOf(2));
        return exposures + " exposures " + oneDecimal(separationDegrees) + " degrees apart, each carrying "
                + oneDecimal(carried) + " degrees";
    }

    private static String oneDecimal(BigDecimal value) {
        return Decimals.halfEven(value, 1).toPlainString();
    }
}
