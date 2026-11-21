package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * The three knobs on a plate scanner, as they were set for one scan.
 *
 * <p>Scan pitch is the step the laser takes; it sets the pixel size and the ceiling on resolution.
 * Gain is the photomultiplier voltage: high gain reads a thin plate at the cost of noise, the same
 * trade as film speed made after the shot rather than before, which is what makes it dangerous.
 * Latitude is the range of exposure the scan covers; too narrow and part of the image clips.
 *
 * @param pitch the scan pitch
 * @param gain the gain setting, one to ten
 * @param latitude the latitude setting, one to five
 */
public record ScanSettings(Length pitch, int gain, int latitude) {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    /**
     * Scanner settings.
     *
     * @throws ValidationError for a pitch that is not positive, or a gain or latitude off the dial
     */
    public ScanSettings {
        if (pitch.um() <= 0) {
            throw new ValidationError("a scan has a pitch");
        }
        if (gain < 1 || gain > 10) {
            throw new ValidationError("gain runs from one to ten", Context.of("gain", gain));
        }
        if (latitude < 1 || latitude > 5) {
            throw new ValidationError("latitude runs from one to five", Context.of("latitude", latitude));
        }
    }

    /**
     * What the gain setting costs in signal to noise, as a factor: one at gain one, doubling every
     * four steps.
     */
    public BigDecimal noisePenalty() {
        BigDecimal steps = Decimals.multiply(Decimals.ln(BigDecimal.valueOf(2)), BigDecimal.valueOf(gain - 1));
        return Decimals.exp(Decimals.divide(steps, FOUR));
    }

    /** {@code 0.020 mm pitch, gain 3, latitude 3}. */
    public String describe() {
        return pitch.formatMm(3) + " pitch, gain " + gain + ", latitude " + latitude;
    }
}
