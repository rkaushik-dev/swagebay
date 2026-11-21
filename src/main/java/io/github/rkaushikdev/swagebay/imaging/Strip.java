package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One processor control strip, read at three points.
 *
 * <p>A pre exposed step wedge goes through the processor every morning. Speed is the density at the
 * middle step, contrast is the difference between two steps either side of it, and fog is the
 * density of an unexposed corner.
 *
 * @param readOn the day the strip was read
 * @param speed the density at the middle step
 * @param contrast the difference between the steps either side of it
 * @param fog the density of the unexposed corner
 * @param operator who read and signed it
 */
public record Strip(LocalDate readOn, BigDecimal speed, BigDecimal contrast, BigDecimal fog, String operator) {

    /**
     * A strip.
     *
     * @throws ValidationError for an unsigned strip or a reading that is not positive
     */
    public Strip {
        if (Text.isBlank(operator)) {
            throw new ValidationError("a control strip is signed");
        }
        requirePositive("speed", speed);
        requirePositive("contrast", contrast);
        requirePositive("fog", fog);
    }

    private static void requirePositive(String reading, BigDecimal value) {
        if (value.signum() <= 0) {
            throw new ValidationError("a strip reading has to be positive", Context.of("reading", reading));
        }
    }
}
