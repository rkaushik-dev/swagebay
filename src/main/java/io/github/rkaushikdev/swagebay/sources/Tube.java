package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * An x-ray set, as its certificate describes it.
 *
 * <p>A gamma source is a fixed lump of activity that decays. A tube is a machine with two knobs.
 * Kilovoltage sets the penetrating power and is chosen for the thickness: the lowest that will
 * penetrate, because a harder beam is less selective about what it passes and the contrast
 * collapses. Milliampere seconds set the quantity and scale linearly. The focal spot is the third
 * number, and a set with a 1.5 mm spot cannot make the same geometry as one with a 0.4 mm spot.
 *
 * @param key the key the set is filed under
 * @param name the model name
 * @param manufacturer who makes it
 * @param minKv the lowest kilovoltage it will make
 * @param maxKv the highest kilovoltage it will make
 * @param maxMa the most current it will give, in milliamperes
 * @param focalSpot the focal spot on the certificate
 * @param dutyCycle the fraction of a cycle the set may be energised for, which limits a long shot
 */
public record Tube(
        String key,
        String name,
        String manufacturer,
        int minKv,
        int maxKv,
        BigDecimal maxMa,
        Length focalSpot,
        BigDecimal dutyCycle) {

    /**
     * A set as its certificate describes it.
     *
     * @throws ValidationError for a set with no kilovoltage range, no current, no focal spot, or a
     *     duty cycle outside (0, 1]
     */
    public Tube {
        if (minKv <= 0 || maxKv < minKv) {
            throw new ValidationError("a tube has a kilovoltage range", Context.of("key", key));
        }
        if (maxMa.signum() <= 0) {
            throw new ValidationError("a tube has a current", Context.of("key", key));
        }
        if (focalSpot.um() <= 0) {
            throw new ValidationError("a tube has a focal spot", Context.of("key", key));
        }
        if (dutyCycle.signum() <= 0 || dutyCycle.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError("a duty cycle sits in (0, 1]", Context.of("key", key));
        }
    }

    /** Whether the set will make that kilovoltage, both ends of its range included. */
    public boolean canReach(int kv) {
        return minKv <= kv && kv <= maxKv;
    }

    /**
     * Refuse a kilovoltage the set cannot make.
     *
     * @throws TechniqueError naming the set's range
     */
    public void checkKv(int kv) {
        if (!canReach(kv)) {
            throw new TechniqueError(
                    "that kilovoltage is outside the set",
                    Context.of("tube", key, "kv", kv, "range", minKv + " to " + maxKv));
        }
    }

    /**
     * Refuse a current the set will not give.
     *
     * @throws ValidationError for a current that is not positive
     * @throws TechniqueError for a current above the set's rating
     */
    public void checkCurrent(BigDecimal ma) {
        if (ma.signum() <= 0) {
            throw new ValidationError("a current has to be positive", Context.of("ma", ma.toString()));
        }
        if (ma.compareTo(maxMa) > 0) {
            throw new TechniqueError(
                    "that current is over what the set will give",
                    Context.of("tube", key, "ma", ma.toString(), "max_ma", maxMa.toString()));
        }
    }

    /** How long the set has to rest before the next shot of that length, to the millisecond below. */
    public Duration coolingAfter(Duration energised) {
        BigDecimal rest = Decimals.divide(Decimals.subtract(BigDecimal.ONE, dutyCycle), dutyCycle);
        return new Duration(Decimals.truncate(Decimals.multiply(BigDecimal.valueOf(energised.ms()), rest)));
    }

    /**
     * Which equivalence table column this beam reads on.
     *
     * @throws TechniqueError for a kilovoltage the set cannot make
     */
    public Quality qualityAt(int kv) {
        checkKv(kv);
        return Quality.forKilovoltage(kv)
                .orElseThrow(() -> new TechniqueError(
                        "no equivalence column covers that kilovoltage", Context.of("tube", key, "kv", kv)));
    }

    /** The name and the range: {@code ERESCO 42 MF4 10 to 200 kV}. */
    @Override
    public String toString() {
        return name + " " + minKv + " to " + maxKv + " kV";
    }
}
