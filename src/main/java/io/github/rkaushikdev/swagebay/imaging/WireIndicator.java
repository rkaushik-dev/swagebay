package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * One wire out of an ASTM E747 or ISO 19232 set.
 *
 * <p>Seeing the essential wire means the technique resolves a feature of that width. A higher
 * number is a thinner wire.
 *
 * @param number the ISO 19232 wire number, 1 to 19
 */
public record WireIndicator(int number) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * A wire from the ISO series.
     *
     * @throws ConfigurationError for a number outside the series
     */
    public WireIndicator {
        if (!Indicators.WIRE_DIAMETERS.containsKey(number)) {
            throw new ConfigurationError("no such wire number", Context.of("number", number));
        }
    }

    /** The wire diameter. */
    public Length diameter() {
        return Indicators.WIRE_DIAMETERS.get(number);
    }

    /** The wire as ISO names it: {@code W13}. */
    public String isoLabel() {
        return "W" + number;
    }

    /**
     * The wire diameter as a percentage of what the beam crossed.
     *
     * @throws ValidationError when the penetrated thickness is not positive
     */
    public BigDecimal sensitivityAt(Length penetrated) {
        Indicators.requirePenetrated(penetrated);
        return Decimals.multiply(diameter().ratio(penetrated), HUNDRED);
    }

    @Override
    public String toString() {
        return isoLabel();
    }
}
