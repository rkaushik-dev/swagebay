package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A computed radiography phosphor plate, as its characterization report describes it.
 *
 * <p>A plate holds its latent image in trapped electrons and the traps leak, so the image starts
 * fading the moment the exposure stops. The fade is roughly exponential with a half life that is a
 * property of the plate. Nothing here pretends a correction recovers the lost information: the
 * plate reports the fraction remaining so a technique can be planned with margin.
 *
 * <p>A finer plate resolves more and needs more exposure to reach the same signal to noise, and a
 * plate that meets a specification on resolution can still fail it on noise, so both numbers are
 * checked.
 *
 * @param key the key the plate is filed under
 * @param name the trade name
 * @param manufacturer who makes it
 * @param ipClass its plate class, {@code IP1} finest
 * @param basicSpatialResolution the measured basic spatial resolution
 * @param normalisedSnr the normalised signal to noise
 * @param fadeHalfLife how long the latent image takes to lose half its signal
 */
public record PlateSystem(
        String key,
        String name,
        String manufacturer,
        String ipClass,
        Length basicSpatialResolution,
        int normalisedSnr,
        Duration fadeHalfLife) {

    /**
     * A plate system.
     *
     * @throws ValidationError for a resolution, noise figure or half life that is not positive
     */
    public PlateSystem {
        if (basicSpatialResolution.um() <= 0) {
            throw new ValidationError("a plate needs a resolution", Context.of("key", key));
        }
        if (normalisedSnr <= 0) {
            throw new ValidationError("a plate needs a signal to noise figure", Context.of("key", key));
        }
        if (fadeHalfLife.ms() <= 0) {
            throw new ValidationError("a plate fades", Context.of("key", key));
        }
    }

    /** The fraction of the latent image still there when the plate is scanned after that wait. */
    public BigDecimal remainingSignal(Duration waited) {
        BigDecimal periods = Decimals.divide(BigDecimal.valueOf(waited.ms()), BigDecimal.valueOf(fadeHalfLife.ms()));
        return Decimals.exp(Decimals.multiply(periods.negate(), Decimals.ln(BigDecimal.valueOf(2))));
    }

    /** Whether the scan came soon enough that nobody bothers correcting for the fade. */
    public boolean scanIsPrompt(Duration waited) {
        return waited.compareTo(Plates.SCAN_WITHOUT_CORRECTION) <= 0;
    }

    /** Whether the scan came before the deadline past which the plate is scrapped. */
    public boolean scanIsUsable(Duration waited) {
        return waited.compareTo(Plates.SCAN_DEADLINE) <= 0;
    }

    /** Whether the plate resolves at least as finely as required. */
    public boolean meetsResolution(Length required) {
        return basicSpatialResolution.compareTo(required) <= 0;
    }

    /** Whether the plate's normalised signal to noise reaches the required figure. */
    public boolean meetsNoise(int required) {
        return normalisedSnr >= required;
    }

    /** Every half of the specification the plate misses, resolution first; empty when it clears both. */
    public List<String> shortfalls(Length requiredResolution, int requiredSnr) {
        List<String> out = new ArrayList<>();
        if (!meetsResolution(requiredResolution)) {
            out.add("resolution " + basicSpatialResolution.formatMm(3) + " is coarser than "
                    + requiredResolution.formatMm(3));
        }
        if (!meetsNoise(requiredSnr)) {
            out.add("normalised snr " + normalisedSnr + " is under " + requiredSnr);
        }
        return List.copyOf(out);
    }

    /** {@code HD-IP (IP1)}. */
    @Override
    public String toString() {
        return name + " (" + ipClass + ")";
    }
}
