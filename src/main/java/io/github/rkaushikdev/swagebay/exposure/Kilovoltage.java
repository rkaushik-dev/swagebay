package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.Tube;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Choosing a kilovoltage, which is a choice and not a lookup.
 *
 * <p>The table gives the lowest kilovoltage that will get through a thickness of steel in a
 * reasonable time, and the reason to start low is contrast: a soft beam is selective about what it
 * passes, so a flaw shows as a bigger difference in density. The choice moves up from there only
 * for a reason it writes down: the set will not go that low, or a denser film needs more exposure.
 * A client specification can cap it to protect contrast, and a cap that bites means the job takes
 * longer rather than the kilovoltage going up, which is the client having decided the trade in
 * advance.
 */
public final class Kilovoltage {

    /** How far the kilovoltage moves when it has to move at all. */
    public static final int CONTRAST_STEP = 20;

    /** The density a choice is made for unless the procedure asks for another. */
    public static final BigDecimal DEFAULT_DENSITY = new BigDecimal("2.0");

    private static final BigDecimal DENSE = new BigDecimal("2.5");

    private static final List<Band> STEEL_KV = List.of(
            band("3", 90),
            band("6", 120),
            band("10", 140),
            band("13", 150),
            band("19", 180),
            band("25", 200),
            band("32", 250),
            band("38", 280),
            band("50", 320),
            band("65", 400),
            band("80", 450));

    /**
     * One row of the table.
     *
     * @param ceiling the thickest steel the row covers, itself included
     * @param kv the lowest workable kilovoltage for it
     */
    public record Band(Length ceiling, int kv) {}

    private Kilovoltage() {}

    /**
     * The table value, which is where every choice starts.
     *
     * @throws ValidationError for a thickness that is not positive
     * @throws TechniqueError for a thickness past the end of the table
     */
    public static int lowestWorkable(Length steelThickness) {
        if (steelThickness.um() <= 0) {
            throw new ValidationError("a thickness has to be positive");
        }
        for (Band band : STEEL_KV) {
            if (steelThickness.compareTo(band.ceiling()) <= 0) {
                return band.kv();
            }
        }
        throw new TechniqueError(
                "that thickness is past the end of the kilovoltage table",
                Context.of("thickness_mm", steelThickness.mm().toString(), "table_ends_at_mm", "80"));
    }

    /** {@link #choose(Length, Tube, BigDecimal, OptionalInt)} at the default density with no cap. */
    public static KilovoltageChoice choose(Length steelThickness, Tube tube) {
        return choose(steelThickness, tube, DEFAULT_DENSITY, OptionalInt.empty());
    }

    /** {@link #choose(Length, Tube, BigDecimal, OptionalInt)} with no cap. */
    public static KilovoltageChoice choose(Length steelThickness, Tube tube, BigDecimal density) {
        return choose(steelThickness, tube, density, OptionalInt.empty());
    }

    /**
     * Start at the table and push up only for a reason that is written down.
     *
     * @param density the density the film has to reach; 2.5 and above costs a contrast step
     * @param ceilingKv the highest kilovoltage the specification allows, when it caps one
     * @throws TechniqueError when the set will not reach the kilovoltage the thickness needs, or the
     *     cap is below what will penetrate
     */
    public static KilovoltageChoice choose(
            Length steelThickness, Tube tube, BigDecimal density, OptionalInt ceilingKv) {
        int table = lowestWorkable(steelThickness);
        int kv = table;
        List<String> reasons = new ArrayList<>();

        if (kv < tube.minKv()) {
            kv = tube.minKv();
            reasons.add("the set will not go below " + tube.minKv());
        }
        if (density.compareTo(DENSE) >= 0) {
            kv += CONTRAST_STEP;
            reasons.add("density " + density + " needs more exposure");
        }
        if (kv > tube.maxKv()) {
            throw new TechniqueError(
                    "the set will not reach the kilovoltage this thickness needs",
                    Context.of("needed", kv, "max_kv", tube.maxKv(), "tube", tube.key()));
        }
        if (ceilingKv.isPresent()) {
            int cap = ceilingKv.getAsInt();
            if (cap < table) {
                throw new TechniqueError(
                        "the specification caps the kilovoltage below what will penetrate",
                        Context.of("cap", cap, "needed", table));
            }
            if (kv > cap) {
                kv = cap;
                reasons.add("the specification caps it at " + cap);
            }
        }
        return new KilovoltageChoice(kv, table, reasons);
    }

    /**
     * A rough index of the contrast given up, for a note on the sheet: a tenth for every step above
     * the table value.
     *
     * <p>The number is not a measurement of anything and does not pretend to be. It exists so two
     * techniques for the same weld can be compared on more than exposure time, and so that a sheet
     * three steps up carries a visible mark.
     */
    public static BigDecimal contrastPenalty(KilovoltageChoice choice) {
        if (choice.stepsAboveTable() <= 0) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(BigDecimal.valueOf(choice.stepsAboveTable()), 10);
    }

    /** The table, thinnest band first. */
    public static List<Band> tableBands() {
        return STEEL_KV;
    }

    private static Band band(String ceilingMm, int kv) {
        return new Band(Length.fromMm(ceilingMm), kv);
    }
}
