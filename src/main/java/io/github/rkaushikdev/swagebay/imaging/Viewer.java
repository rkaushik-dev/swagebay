package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * A film illuminator, as its plate says.
 *
 * <p>A viewer with a stated maximum luminance can support densities up to the point where the light
 * coming through falls below {@link Viewers#MINIMUM_TRANSMITTED}, the light an eye can work with.
 * Above that density the film is dark to the reader however good it is:
 *
 * <pre>supported density = log10(viewer luminance / 30)</pre>
 *
 * @param key the key the viewer is filed under
 * @param name what it is called
 * @param maximumLuminance candela a square metre with nothing in front of it
 * @param masked whether it masks the light around the film
 */
public record Viewer(String key, String name, int maximumLuminance, boolean masked) {

    private static final BigDecimal TEN = BigDecimal.TEN;

    /**
     * A viewer.
     *
     * @throws ValidationError for a luminance that is not positive
     */
    public Viewer {
        if (maximumLuminance <= 0) {
            throw new ValidationError("a viewer has a luminance", Context.of("key", key));
        }
    }

    /** The densest film this viewer can push enough light through. */
    public BigDecimal supportedDensity() {
        BigDecimal ratio = Decimals.divide(BigDecimal.valueOf(maximumLuminance), Viewers.MINIMUM_TRANSMITTED);
        return Decimals.divide(Decimals.ln(ratio), Decimals.ln(TEN));
    }

    /** Whether the viewer can read a film that dense. */
    public boolean supports(BigDecimal density) {
        return density.compareTo(supportedDensity()) <= 0;
    }

    /**
     * The light that reaches the reader through a film of that density, in candela a square metre.
     *
     * @throws ValidationError for a negative density
     */
    public BigDecimal transmittedAt(BigDecimal density) {
        if (density.signum() < 0) {
            throw new ValidationError("a density is not negative");
        }
        return Decimals.divide(BigDecimal.valueOf(maximumLuminance), Decimals.power(TEN, density));
    }

    /** {@code HiLight 300 30000 cd/m2 masked}. */
    @Override
    public String toString() {
        return name + " " + maximumLuminance + " cd/m2 " + (masked ? "masked" : "unmasked");
    }
}
