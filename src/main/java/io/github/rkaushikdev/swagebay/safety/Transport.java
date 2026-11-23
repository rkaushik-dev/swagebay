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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Getting a source to the job, which is regulated harder than using it.
 *
 * <p>A projector in the back of a truck is a radioactive material package on a public road, and the
 * rules come from the transport regulations rather than the radiography licence. The package has to
 * be rated for the contents: a sealed radiography source is special form, so a Type A package may
 * carry up to the A1 value for the isotope, and above that it takes a Type B. A source whose special
 * form certificate has run out is held to the much lower A2 value instead. The label depends on
 * two numbers, the rate at the surface and the rate at a metre. The metre reading in millirem an
 * hour, rounded up to a tenth, is the transport index, and the vehicle has a ceiling on the sum of
 * the indices aboard.
 */
public final class Transport {

    /** The sum of transport indices allowed on one exclusive use vehicle. */
    public static final BigDecimal VEHICLE_INDEX_CEILING = BigDecimal.valueOf(50);

    /**
     * A1 and A2 in terabecquerels, by isotope. A1 is for special form material, which a sealed
     * radiography source is while its special form certificate is current; A2 is for everything else.
     */
    private static final Map<String, Limits> LIMITS = limits(
            "ir192  1    0.6",
            "co60   0.4  0.4",
            "se75   3    3",
            "cs137  2    0.6",
            "yb169  4    1",
            "tm170  3    0.8");

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
        return Activity.fromTbq(limitsFor(isotope).a1());
    }

    /**
     * The most of that isotope a Type A package may carry when it is not special form, which is
     * where a source whose special form certificate has lapsed ends up.
     *
     * @throws ConfigurationError for an isotope with no published limit
     */
    public static Activity a2Limit(Isotope isotope) {
        return Activity.fromTbq(limitsFor(isotope).a2());
    }

    /**
     * The Type A limit for that isotope, A1 for special form and A2 otherwise.
     *
     * @throws ConfigurationError for an isotope with no published limit
     */
    public static Activity typeALimit(Isotope isotope, boolean specialForm) {
        return specialForm ? a1Limit(isotope) : a2Limit(isotope);
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

    /** One isotope's two Type A limits, in terabecquerels as published. */
    private record Limits(String a1, String a2) {}

    private static Limits limitsFor(Isotope isotope) {
        Limits found = LIMITS.get(isotope.key());
        if (found == null) {
            throw new ConfigurationError(
                    "no transport limit is published for that isotope", Context.of("isotope", isotope.key()));
        }
        return found;
    }

    private static Map<String, Limits> limits(String... rows) {
        Map<String, Limits> out = new LinkedHashMap<>();
        for (String row : rows) {
            String[] cells = row.trim().split("\\s+");
            out.put(cells[0], new Limits(cells[1], cells[2]));
        }
        return Collections.unmodifiableMap(out);
    }
}
