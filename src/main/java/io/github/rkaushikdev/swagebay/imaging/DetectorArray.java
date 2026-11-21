package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One digital detector array, characterized to E2597.
 *
 * <p>A panel is qualified rather than trusted, and the qualification is a set of independent
 * numbers that each fail differently. Basic spatial resolution is not the pixel pitch: it is worse,
 * because the scintillator spreads light sideways, and quoting pitch as resolution is the most
 * common lie in a detector datasheet. Efficiency says how much dose it takes to reach a given signal
 * to noise. Image lag is the fraction of the previous frame still present in this one. Burn in is
 * lag that never goes away, and a panel with burn in is scrap.
 *
 * @param key the key the panel is filed under
 * @param name the trade name
 * @param manufacturer who makes it
 * @param pixelPitch the pixel pitch
 * @param basicSpatialResolution the measured basic spatial resolution, never finer than the pitch
 * @param normalisedSnr the normalised signal to noise
 * @param efficiency the detector efficiency
 * @param imageLag the fraction of the previous frame left in this one, in [0, 1]
 * @param burnIn the fraction that never washes out, in [0, 1]
 * @param columns the width in pixels
 * @param rows the height in pixels
 */
public record DetectorArray(
        String key,
        String name,
        String manufacturer,
        Length pixelPitch,
        Length basicSpatialResolution,
        int normalisedSnr,
        BigDecimal efficiency,
        BigDecimal imageLag,
        BigDecimal burnIn,
        int columns,
        int rows) {

    /**
     * The active area of a panel.
     *
     * @param width the pitch times the columns
     * @param height the pitch times the rows
     */
    public record Area(Length width, Length height) {}

    /**
     * A characterized panel.
     *
     * @throws ValidationError for a pitch or size that is not positive, a resolution finer than the
     *     pitch, or a lag or burn in outside [0, 1]
     */
    public DetectorArray {
        if (pixelPitch.um() <= 0) {
            throw new ValidationError("a panel needs a pixel pitch", Context.of("key", key));
        }
        if (basicSpatialResolution.compareTo(pixelPitch) < 0) {
            throw new ValidationError(
                    "resolution cannot be finer than the pitch that produced it",
                    Context.of(
                            "key", key,
                            "pitch_um", pixelPitch.um(),
                            "resolution_um", basicSpatialResolution.um()));
        }
        if (columns <= 0 || rows <= 0) {
            throw new ValidationError("a panel has a size in pixels", Context.of("key", key));
        }
        requireFraction(key, "image_lag", imageLag);
        requireFraction(key, "burn_in", burnIn);
    }

    /** The pixel count. */
    public long pixels() {
        return (long) columns * rows;
    }

    /** How much worse the resolution is than the pitch would suggest. */
    public BigDecimal resolutionPenalty() {
        return basicSpatialResolution.ratio(pixelPitch);
    }

    /** The width and height the pixels cover. */
    public Area activeArea() {
        return new Area(pixelPitch.times(columns), pixelPitch.times(rows));
    }

    /** Whether the image lag is inside {@link Detectors#MAX_ACCEPTABLE_LAG}. */
    public boolean lagIsAcceptable() {
        return imageLag.compareTo(Detectors.MAX_ACCEPTABLE_LAG) <= 0;
    }

    /** Burn in does not wash out, so a panel with more than {@link Detectors#MAX_ACCEPTABLE_BURN_IN} is finished. */
    public boolean isScrap() {
        return burnIn.compareTo(Detectors.MAX_ACCEPTABLE_BURN_IN) > 0;
    }

    /** Every way the panel misses the specification or its own limits; empty when it clears all of them. */
    public List<String> shortfalls(Length requiredResolution, int requiredSnr) {
        List<String> out = new ArrayList<>();
        if (basicSpatialResolution.compareTo(requiredResolution) > 0) {
            out.add("resolution " + basicSpatialResolution.formatMm(3) + " is coarser than "
                    + requiredResolution.formatMm(3));
        }
        if (normalisedSnr < requiredSnr) {
            out.add("normalised snr " + normalisedSnr + " is under " + requiredSnr);
        }
        if (!lagIsAcceptable()) {
            out.add("image lag " + imageLag + " is over " + Detectors.MAX_ACCEPTABLE_LAG);
        }
        if (isScrap()) {
            out.add("burn in " + burnIn + " is over " + Detectors.MAX_ACCEPTABLE_BURN_IN);
        }
        return List.copyOf(out);
    }

    /** {@code DXR250C 1024x1024}. */
    @Override
    public String toString() {
        return name + " " + columns + "x" + rows;
    }

    private static void requireFraction(String key, String field, BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError("a fraction sits in [0, 1]", Context.of("key", key, "field", field));
        }
    }
}
