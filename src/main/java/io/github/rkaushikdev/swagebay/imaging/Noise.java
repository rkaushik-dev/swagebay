package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;

/**
 * Signal to noise on a digital image, and why the raw figure is not comparable to anything.
 *
 * <p>A coarse system averages over a bigger area per pixel, so it reports a better signal to noise
 * for the same physics, and a panel with twice the pixel size can look twice as good while showing
 * half as much. The standard fixes that by normalising to a reference basic spatial resolution of
 * 88.6 micrometres:
 *
 * <pre>SNRn = SNR x sqrt(88.6 / SRb in micrometres)</pre>
 *
 * <p>A fine system gets scaled up, a coarse one down, and after that the numbers mean the same thing
 * on both. The reference is held as a {@link Length}, so it is the nearest whole micrometre, 89.
 *
 * <p>The required figure depends on penetrated thickness and on the technique class, and it is the
 * number a client argues about, so the table carries the class it came from.
 */
public final class Noise {

    /** The 88.6 micrometre reference every normalised figure is scaled to. */
    public static final Length REFERENCE_RESOLUTION = Length.fromMm("0.0886");

    private record Band(Length ceiling, int floor) {}

    private static final List<Band> REQUIRED_A =
            List.of(band("10", 70), band("20", 80), band("40", 90), band("100", 100), band("250", 110));

    private static final List<Band> REQUIRED_B =
            List.of(band("10", 100), band("20", 110), band("40", 120), band("100", 130), band("250", 140));

    private Noise() {}

    /**
     * Scale a raw figure to the reference resolution.
     *
     * @throws ValidationError for a figure or a resolution that is not positive
     */
    public static BigDecimal normalise(BigDecimal signalToNoise, Length resolution) {
        if (signalToNoise.signum() <= 0) {
            throw new ValidationError("a signal to noise figure has to be positive");
        }
        if (resolution.um() <= 0) {
            throw new ValidationError("a resolution has to be positive");
        }
        BigDecimal ratio = Decimals.divide(REFERENCE_RESOLUTION.um(), resolution.um());
        return Decimals.multiply(signalToNoise, Decimals.sqrt(ratio));
    }

    /**
     * What a system at that resolution has to read raw to reach a normalised figure.
     *
     * @throws ValidationError for a figure or a resolution that is not positive
     */
    public static BigDecimal denormalise(BigDecimal normalisedSnr, Length resolution) {
        if (normalisedSnr.signum() <= 0) {
            throw new ValidationError("a normalised figure has to be positive");
        }
        if (resolution.um() <= 0) {
            throw new ValidationError("a resolution has to be positive");
        }
        BigDecimal ratio = Decimals.divide(resolution.um(), REFERENCE_RESOLUTION.um());
        return Decimals.multiply(normalisedSnr, Decimals.sqrt(ratio));
    }

    /**
     * The normalised floor for that penetrated thickness and class. Bands are closed at the top.
     *
     * @throws ValidationError for a thickness that is not positive
     * @throws TechniqueError past the end of the table
     */
    public static int requiredNormalisedSnr(Length penetrated, TechniqueClass techniqueClass) {
        if (penetrated.um() <= 0) {
            throw new ValidationError("a penetrated thickness has to be positive");
        }
        List<Band> table = techniqueClass.isImproved() ? REQUIRED_B : REQUIRED_A;
        for (Band band : table) {
            if (penetrated.compareTo(band.ceiling()) <= 0) {
                return band.floor();
            }
        }
        throw new TechniqueError(
                "that penetrated thickness is past the end of the noise table",
                Context.of("penetrated_mm", penetrated.mm().toString(), "technique_class", techniqueClass.value()));
    }

    /** Whether the measurement's normalised figure reaches the floor. */
    public static boolean meetsRequirement(
            NoiseMeasurement measurement, Length penetrated, TechniqueClass techniqueClass) {
        return measurement.normalised().compareTo(BigDecimal.valueOf(requiredNormalisedSnr(penetrated, techniqueClass)))
                >= 0;
    }

    /** How far under the floor the image is; zero when it clears. */
    public static BigDecimal shortfall(NoiseMeasurement measurement, Length penetrated, TechniqueClass techniqueClass) {
        int floor = requiredNormalisedSnr(penetrated, techniqueClass);
        BigDecimal gap = Decimals.subtract(BigDecimal.valueOf(floor), measurement.normalised());
        return gap.signum() > 0 ? gap : BigDecimal.ZERO;
    }

    private static Band band(String ceilingMm, int floor) {
        return new Band(Length.fromMm(ceilingMm), floor);
    }
}
