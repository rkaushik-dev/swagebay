package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * A gamma emitter, with what planning and safety both need from it.
 *
 * <p>Four numbers decide whether an isotope is the right one for a weld. The half life says how
 * long a source stays useful and therefore what it costs to own. The gamma constant says how hot it
 * is at a metre, which is the whole of the safety calculation. The energy says how well it
 * penetrates. The practical thickness range is the trade answer to all three together, and it is
 * narrower than the physics allows because outside it the contrast is not good enough to find
 * anything.
 *
 * <p>ASTM E94 publishes equivalence factor columns for iridium and cobalt and for x-ray qualities,
 * and for nothing else. An isotope without a column reports no equivalence quality rather than
 * borrowing a neighbour's, and the caller has to decide what to do about it.
 *
 * @param key the key the isotope is filed under
 * @param name the name in words, {@code iridium 192}
 * @param symbol the symbol stencilled on the pig, {@code Ir-192}
 * @param halfLifeDays the half life in days
 * @param gammaConstant roentgen an hour at one metre from one curie
 * @param energyKevLow the lowest line of the spectrum worth counting, in keV
 * @param energyKevHigh the highest line of the spectrum worth counting, in keV
 * @param steelMin the thinnest steel wall it is the trade answer for
 * @param steelMax the thickest steel wall it is the trade answer for
 * @param equivalenceQuality the E94 column to read, where the standard publishes one
 */
public record Isotope(
        String key,
        String name,
        String symbol,
        BigDecimal halfLifeDays,
        BigDecimal gammaConstant,
        int energyKevLow,
        int energyKevHigh,
        Length steelMin,
        Length steelMax,
        Optional<Quality> equivalenceQuality) {

    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24);
    private static final int BROAD_SPECTRUM_KEV = 200;

    /** The half life as a span of time. */
    public Duration halfLife() {
        return Duration.fromHours(Decimals.multiply(halfLifeDays, HOURS_PER_DAY));
    }

    /** Whether the spectrum is a spread rather than a few clean lines, as cobalt's two are. */
    public boolean isBroadSpectrum() {
        return energyKevHigh - energyKevLow > BROAD_SPECTRUM_KEV;
    }

    /** Whether this isotope is the trade answer for that wall, both ends of the range included. */
    public boolean covers(Length steelThickness) {
        return steelMin.compareTo(steelThickness) <= 0 && steelThickness.compareTo(steelMax) <= 0;
    }

    /**
     * The E94 column to read.
     *
     * @throws ConfigurationError when the standard publishes none for this isotope
     */
    public Quality qualityForEquivalence() {
        return equivalenceQuality.orElseThrow(() -> new ConfigurationError(
                "astm e94 publishes no equivalence column for this isotope", Context.of("isotope", key)));
    }

    /** The symbol on the pig. */
    @Override
    public String toString() {
        return symbol;
    }
}
