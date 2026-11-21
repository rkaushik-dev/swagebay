package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * A film and screen combination the shop stocks.
 *
 * <p>What matters about a film for planning is its speed, because every exposure chart is drawn
 * for one film and shooting another means scaling the time. Doubling the speed halves the time,
 * which is the whole of the arithmetic and also the whole of the trap: a faster film is grainier,
 * and the grain sets the smallest flaw that can be seen at all.
 *
 * @param key the key the film is filed under
 * @param name the trade name
 * @param manufacturer who makes it
 * @param filmClass its system class
 * @param speedIndex relative speed with the reference film at 100; bigger is faster
 * @param granularity root mean square granularity in optical density units at 2.0 D
 */
public record FilmSystem(
        String key,
        String name,
        String manufacturer,
        FilmClass filmClass,
        BigDecimal speedIndex,
        BigDecimal granularity) {

    /**
     * A film system.
     *
     * @throws ValidationError for a speed index that is not positive
     */
    public FilmSystem {
        if (speedIndex.signum() <= 0) {
            throw new ValidationError("a film speed index has to be positive", Context.of("key", key));
        }
    }

    /** Multiply a chart time for the reference film by this to get the time for this film. */
    public BigDecimal timeFactorFrom(FilmSystem reference) {
        return Decimals.divide(reference.speedIndex, speedIndex);
    }

    /** Whether this film's grain is finer than that one's. */
    public boolean isFinerThan(FilmSystem other) {
        return granularity.compareTo(other.granularity) < 0;
    }
}
