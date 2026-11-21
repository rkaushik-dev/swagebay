package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;

/**
 * One box drawn on one digital image: the mean grey value and its standard deviation.
 *
 * @param label where on the image the box was drawn
 * @param meanGrey the mean grey value inside it
 * @param standardDeviation the noise inside it
 * @param resolution the basic spatial resolution of the system the image was made on
 */
public record NoiseMeasurement(String label, BigDecimal meanGrey, BigDecimal standardDeviation, Length resolution) {

    /**
     * A measurement.
     *
     * @throws ValidationError for a blank label, or a mean, noise or resolution that is not positive
     */
    public NoiseMeasurement {
        if (Text.isBlank(label)) {
            throw new ValidationError("a noise measurement needs a location");
        }
        if (meanGrey.signum() <= 0) {
            throw new ValidationError("a mean grey value has to be positive", Context.of("label", label));
        }
        if (standardDeviation.signum() <= 0) {
            throw new ValidationError("noise of zero is not a measurement", Context.of("label", label));
        }
        if (resolution.um() <= 0) {
            throw new ValidationError("a measurement needs the resolution it was made at");
        }
    }

    /** The raw signal to noise, mean over deviation. */
    public BigDecimal signalToNoise() {
        return Decimals.divide(meanGrey, standardDeviation);
    }

    /** The signal to noise scaled to the reference resolution, which is the comparable figure. */
    public BigDecimal normalised() {
        return Noise.normalise(signalToNoise(), resolution);
    }

    /** {@code parent metal snr 120.0 normalised 120.0}. */
    @Override
    public String toString() {
        return label + " snr " + Decimals.halfEven(signalToNoise(), 1).toPlainString() + " normalised "
                + Decimals.halfEven(normalised(), 1).toPlainString();
    }
}
