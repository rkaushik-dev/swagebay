package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One point where the meter and the model disagree by more than scatter. Measured hotter than
 * calculated is a finding; measured colder is a note.
 *
 * @param point the reading
 * @param calculated what the model said the meter should read there
 * @param ratio the reading over the calculation
 */
public record Discrepancy(SurveyPoint point, DoseRate calculated, BigDecimal ratio) {

    /** A discrepancy. */
    public Discrepancy {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(calculated, "calculated");
        Objects.requireNonNull(ratio, "ratio");
    }

    /** Whether the meter read more than the model. */
    public boolean measuredHotter() {
        return ratio.compareTo(BigDecimal.ONE) > 0;
    }

    /**
     * The survey note, the ratio to two places: {@code boundary reads 20.00 mR/h, 10.00 times the
     * calculated 2.00 mR/h, hotter}.
     */
    public String describe() {
        String direction = measuredHotter() ? "hotter" : "colder";
        return point.label() + " reads " + point.reading().formatMrPerHour() + ", "
                + Decimals.halfEven(ratio, 2) + " times the calculated " + calculated.formatMrPerHour() + ", "
                + direction;
    }
}
