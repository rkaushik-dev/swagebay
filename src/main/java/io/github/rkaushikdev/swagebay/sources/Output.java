package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * How hot a source is at a distance, with nothing in the way.
 *
 * <p>The whole of field radiation safety is one equation: the rate is the gamma constant times the
 * curies over the metres squared. The gamma constant is quoted in roentgen an hour at one metre
 * from one curie, which is what every card in every truck is printed in. Halving the distance
 * quadruples the rate; to cut a rate by a hundred, back off by ten.
 *
 * <p>The law holds for a point source in air. Close in it overestimates, because the source is not
 * a point, and far away in a plant it underestimates, because concrete scatters; both errors are on
 * the safe side at the distances boundaries are drawn at. This gives the bare rate and nothing
 * else. A boundary distance depends on what stands between the source and the person, which is a
 * field and not an isotope, and is worked out in the safety package.
 */
public final class Output {

    /** One metre, the distance the gamma constant is quoted at. */
    public static final Length ONE_METRE = Length.fromM(1);

    private static final BigDecimal MR_PER_ROENTGEN = BigDecimal.valueOf(1000);

    private Output() {}

    /** The rate at a metre with nothing in the way. */
    public static DoseRate outputAtOneMetre(Isotope source, Activity activity) {
        BigDecimal roentgenPerHour = Decimals.multiply(source.gammaConstant(), activity.curie());
        return DoseRate.fromMrPerHour(Decimals.multiply(roentgenPerHour, MR_PER_ROENTGEN));
    }

    /**
     * The unshielded rate at any distance, to the nanosievert an hour below.
     *
     * @throws ValidationError for a distance that is not positive
     */
    public static DoseRate doseRateAt(Isotope source, Activity activity, Length distance) {
        if (distance.um() <= 0) {
            throw new ValidationError("a dose rate needs a positive distance", Context.of("um", distance.um()));
        }
        BigDecimal metres = distance.m();
        BigDecimal rate = Decimals.divide(
                BigDecimal.valueOf(outputAtOneMetre(source, activity).nsvPerHour()), Decimals.multiply(metres, metres));
        return new DoseRate(Decimals.truncate(rate));
    }
}
