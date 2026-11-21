package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A step wedge check on the densitometer.
 *
 * <p>A densitometer drifts, so a reading is a number plus the calibration behind it. A check
 * covers one shift from the moment it was made, and a check that failed covers nothing.
 *
 * @param checkedAt when the step was read
 * @param stepNominal the density the step is certified at
 * @param stepRead what the instrument read it as
 * @param instrument which densitometer
 */
public record Calibration(LocalDateTime checkedAt, BigDecimal stepNominal, BigDecimal stepRead, String instrument) {

    /** How far the instrument read off the certified step. */
    public BigDecimal error() {
        return Decimals.subtract(stepRead, stepNominal);
    }

    /** Whether the error is inside {@link Density#STEP_WEDGE_TOLERANCE}. */
    public boolean passed() {
        return error().abs().compareTo(Density.STEP_WEDGE_TOLERANCE) <= 0;
    }

    /** Whether a reading taken at that moment is covered: a passed check, earlier the same shift. */
    public boolean isCurrentAt(LocalDateTime moment) {
        if (!passed()) {
            return false;
        }
        return !checkedAt.isAfter(moment) && moment.isBefore(checkedAt.plus(Density.CALIBRATION_INTERVAL));
    }
}
