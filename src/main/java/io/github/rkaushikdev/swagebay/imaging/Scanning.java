package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading a phosphor plate on the scanner, whose settings are part of the technique either way.
 *
 * <p>None of the scanner settings are on the technique sheet in most shops, which is why the same
 * plate scanned twice on different settings produces two different verdicts and nobody can say
 * which was right. A crew that underexposes and turns the gain up gets an image that passes on
 * density and fails on signal to noise.
 */
public final class Scanning {

    private static final int WORKING_LATITUDE = 3;
    private static final int HIGHEST_GAIN = 10;

    private Scanning() {}

    /** The finest thing a scan at that pitch can resolve, which is two pixels. */
    public static Length resolutionCeiling(ScanSettings settings) {
        return settings.pitch().times(2);
    }

    /** Whichever of the plate and the scan is the coarser. */
    public static Length achievableResolution(PlateSystem plate, ScanSettings settings) {
        Length ceiling = resolutionCeiling(settings);
        return plate.basicSpatialResolution().compareTo(ceiling) >= 0 ? plate.basicSpatialResolution() : ceiling;
    }

    /** Whether the scan is finer than the plate can hold, which is time and disk for nothing. */
    public static boolean isOversampled(PlateSystem plate, ScanSettings settings) {
        return resolutionCeiling(settings).compareTo(plate.basicSpatialResolution()) < 0;
    }

    /** The plate's normalised signal to noise after the gain has been paid for. */
    public static BigDecimal effectiveSnr(PlateSystem plate, ScanSettings settings) {
        return Decimals.divide(BigDecimal.valueOf(plate.normalisedSnr()), settings.noisePenalty());
    }

    /** Everything wrong with reading that plate on those settings: resolution, noise, oversampling. */
    public static List<String> faults(
            PlateSystem plate, ScanSettings settings, Length requiredResolution, int requiredSnr) {
        List<String> out = new ArrayList<>();
        Length achieved = achievableResolution(plate, settings);
        if (achieved.compareTo(requiredResolution) > 0) {
            out.add("resolution " + achieved.formatMm(3) + " is coarser than " + requiredResolution.formatMm(3));
        }
        BigDecimal snr = effectiveSnr(plate, settings);
        if (snr.compareTo(BigDecimal.valueOf(requiredSnr)) < 0) {
            out.add("signal to noise " + Decimals.halfEven(snr, 0).toPlainString() + " is under " + requiredSnr
                    + " at gain " + settings.gain());
        }
        if (isOversampled(plate, settings)) {
            out.add("a " + settings.pitch().formatMm(3) + " pitch is finer than the plate holds, "
                    + "which costs time and gains nothing");
        }
        return List.copyOf(out);
    }

    /** Half the plate resolution, which is as fine as it is worth scanning. */
    public static Length recommendedPitch(PlateSystem plate) {
        return plate.basicSpatialResolution().dividedBy(2);
    }

    /**
     * The lowest gain that still clears the noise floor at the recommended pitch, which is the one to
     * use.
     *
     * @throws ValidationError for a required figure that is not positive
     * @throws TechniqueError when no gain on the scanner clears it
     */
    public static int lowestWorkableGain(PlateSystem plate, int requiredSnr) {
        if (requiredSnr <= 0) {
            throw new ValidationError("a required figure has to be positive");
        }
        for (int gain = 1; gain <= HIGHEST_GAIN; gain++) {
            ScanSettings settings = new ScanSettings(recommendedPitch(plate), gain, WORKING_LATITUDE);
            if (effectiveSnr(plate, settings).compareTo(BigDecimal.valueOf(requiredSnr)) >= 0) {
                return gain;
            }
        }
        throw new TechniqueError(
                "no gain on this scanner clears that noise floor",
                Context.of("plate", plate.key(), "required_snr", requiredSnr));
    }

    /**
     * The settings the office would write on the sheet if it wrote them at all: the recommended
     * pitch, the lowest workable gain and the middle latitude.
     *
     * @throws ConfigurationError for a plate with no noise figure
     */
    public static ScanSettings settingsFor(PlateSystem plate, int requiredSnr) {
        if (plate.normalisedSnr() <= 0) {
            throw new ConfigurationError("that plate has no noise figure", Context.of("plate", plate.key()));
        }
        return new ScanSettings(recommendedPitch(plate), lowestWorkableGain(plate, requiredSnr), WORKING_LATITUDE);
    }
}
