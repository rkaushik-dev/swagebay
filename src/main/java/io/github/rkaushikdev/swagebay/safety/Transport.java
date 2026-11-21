package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Getting a source to the job, which is regulated harder than using it.
 *
 * <p>A projector in the back of a truck is a radioactive material package on a public road, and the
 * rules come from the transport regulations rather than the radiography licence. The package has to
 * be rated for the contents: a sealed radiography source is special form, so a Type A package may
 * carry up to the A1 value for the isotope, and above that it takes a Type B. The label depends on
 * two numbers, the rate at the surface and the rate at a metre. The metre reading in millirem an
 * hour, rounded up to a tenth, is the transport index, and the vehicle has a ceiling on the sum of
 * the indices aboard.
 */
public final class Transport {

    /** The sum of transport indices allowed on one exclusive use vehicle. */
    public static final BigDecimal VEHICLE_INDEX_CEILING = BigDecimal.valueOf(50);

    /** A1 in terabecquerels, for special form material. */
    private static final Map<String, String> A1_TBQ =
            Map.of("ir192", "1", "co60", "0.4", "se75", "3", "cs137", "2", "yb169", "4");

    /** The labels quietest first, each with the most its surface may read and the largest index it may carry. */
    private static final List<Ceiling> CEILINGS = List.of(
            new Ceiling(Label.WHITE_I, DoseRate.fromMsvPerHour("0.005"), BigDecimal.ZERO),
            new Ceiling(Label.YELLOW_II, DoseRate.fromMsvPerHour("0.5"), BigDecimal.ONE),
            new Ceiling(Label.YELLOW_III, DoseRate.fromMsvPerHour(2), BigDecimal.TEN));

    private Transport() {}

    /**
     * The most special form material of that isotope a Type A package may carry.
     *
     * @throws ConfigurationError for an isotope with no published limit
     */
    public static Activity a1Limit(Isotope isotope) {
        String tbq = A1_TBQ.get(isotope.key());
        if (tbq == null) {
            throw new ConfigurationError(
                    "no transport limit is published for that isotope", Context.of("isotope", isotope.key()));
        }
        return Activity.fromTbq(tbq);
    }

    /**
     * The transport index: millirem an hour at a metre, rounded up to a tenth.
     *
     * @throws ValidationError for a negative rate
     */
    public static BigDecimal transportIndex(DoseRate rateAtOneMetre) {
        if (rateAtOneMetre.nsvPerHour() < 0) {
            throw new ValidationError("a transport index needs a rate");
        }
        BigDecimal mrem = Decimals.divide(rateAtOneMetre.usvPerHour(), 10);
        return mrem.setScale(1, RoundingMode.CEILING);
    }

    /**
     * The label a package carries, decided by whichever of the two numbers is worse.
     *
     * @throws TechniqueError for a package over the limits for every label
     */
    public static Label labelFor(DoseRate surfaceRate, DoseRate rateAtOneMetre) {
        BigDecimal index = transportIndex(rateAtOneMetre);
        for (Ceiling ceiling : CEILINGS) {
            if (surfaceRate.compareTo(ceiling.surface()) <= 0 && index.compareTo(ceiling.index()) <= 0) {
                return ceiling.label();
            }
        }
        throw new TechniqueError(
                "the package is over the limits for every label",
                Context.of(
                        "surface_msv_per_hour",
                        surfaceRate.msvPerHour().toString(),
                        "transport_index",
                        index.toString()));
    }

    /** One label's limits. */
    private record Ceiling(Label label, DoseRate surface, BigDecimal index) {}
}
