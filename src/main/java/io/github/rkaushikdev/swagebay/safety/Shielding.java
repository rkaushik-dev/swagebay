package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Shielding, in half value layers.
 *
 * <p>A half value layer is the thickness of something that cuts the rate in half; ten of them cut
 * it by a factor of a thousand and a bit. That is the whole model, and it is deliberately the crude
 * one: broad beam attenuation with scatter buildup already folded into the published layer
 * thicknesses, which is how the numbers in the licence application were derived and therefore how
 * they are derived here, so the two agree.
 *
 * <p>The tables are per isotope and per material and they are not interchangeable: iridium goes
 * through five millimetres of lead the way cobalt goes through twelve, and thulium is stopped by a
 * quarter of one. There is no formula here that
 * turns density into attenuation. A material that is not in the table is refused, and somebody
 * measures the wall with a meter.
 */
public final class Shielding {

    /** Every material the table publishes layers for, sorted, which are its columns. */
    public static final List<String> SHIELD_MATERIALS =
            List.of("concrete", "lead", "steel", "tungsten", "uranium", "water");

    private static final BigDecimal LN_2 = Decimals.ln(BigDecimal.TWO);
    private static final BigDecimal TENTH_OVER_HALF = Decimals.divide(Decimals.ln(BigDecimal.TEN), LN_2);

    /**
     * Half value layers in millimetres, broad beam, from the licence application, one row per
     * isotope with a cell under every material in {@link #SHIELD_MATERIALS}.
     */
    private static final Map<String, Map<String, Length>> HALF_VALUE_LAYERS = table(
            "ir192  44.5   4.8   12.7  2.8   2.0   160.0",
            "co60   62.0   12.5  21.6  7.6   5.5   220.0",
            "cs137  47.0   6.5   16.5  4.0   3.0   180.0",
            "se75   30.0   1.0   8.0   0.7   0.5   120.0",
            "yb169  15.0   0.35  3.5   0.3   0.2   70.0",
            "tm170  13.0   0.25  2.5   0.1   0.15  55.0");

    private Shielding() {}

    /**
     * The thickness that halves the rate, for that isotope in that material.
     *
     * @throws ConfigurationError when the table has nothing for the isotope, or nothing for the
     *     material with that isotope
     */
    public static Length halfValueLayer(Isotope source, String material) {
        Map<String, Length> layers = Optional.ofNullable(HALF_VALUE_LAYERS.get(source.key()))
                .orElseThrow(() -> new ConfigurationError(
                        "no shielding data for that isotope", Context.of("isotope", source.key())));
        return Optional.ofNullable(layers.get(material))
                .orElseThrow(() -> new ConfigurationError(
                        "no half value layer is published for that material",
                        Context.of("isotope", source.key(), "material", material)));
    }

    /**
     * The thickness that cuts the rate to a tenth, which is what the wall charts are drawn in: log ten
     * over log two half value layers, to the micrometre below.
     *
     * @throws ConfigurationError as {@link #halfValueLayer}
     */
    public static Length tenthValueLayer(Isotope source, String material) {
        Length hvl = halfValueLayer(source, material);
        return new Length(Decimals.truncate(Decimals.multiply(BigDecimal.valueOf(hvl.um()), TENTH_OVER_HALF)));
    }

    /**
     * The fraction of the rate that gets through that thickness; all of it through none.
     *
     * @throws ValidationError for a negative thickness
     * @throws ConfigurationError as {@link #halfValueLayer}
     */
    public static BigDecimal transmission(Isotope source, String material, Length thickness) {
        if (thickness.um() < 0) {
            throw new ValidationError("a shield cannot be a negative thickness");
        }
        if (thickness.um() == 0) {
            return BigDecimal.ONE;
        }
        Length hvl = halfValueLayer(source, material);
        BigDecimal layers = Decimals.divide(BigDecimal.valueOf(thickness.um()), BigDecimal.valueOf(hvl.um()));
        return Decimals.exp(Decimals.multiply(layers.negate(), LN_2));
    }

    /**
     * A rate cut down by that thickness, to the nanosievert an hour below.
     *
     * @throws ValidationError for a negative thickness
     * @throws ConfigurationError as {@link #halfValueLayer}
     */
    public static DoseRate attenuated(Isotope source, String material, Length thickness, DoseRate rate) {
        BigDecimal through = transmission(source, material, thickness);
        return new DoseRate(Decimals.truncate(Decimals.multiply(BigDecimal.valueOf(rate.nsvPerHour()), through)));
    }

    /**
     * How much of the material cuts the rate to that fraction, rounded up to the micrometre.
     *
     * @throws ValidationError for a fraction outside (0, 1]
     * @throws ConfigurationError as {@link #halfValueLayer}
     */
    public static Length thicknessForTransmission(Isotope source, String material, BigDecimal fraction) {
        if (fraction.signum() <= 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError(
                    "a transmission fraction sits in (0, 1]", Context.of("fraction", fraction.toString()));
        }
        Length hvl = halfValueLayer(source, material);
        BigDecimal layers = Decimals.divide(Decimals.ln(fraction).negate(), LN_2);
        BigDecimal micrometres = Decimals.multiply(BigDecimal.valueOf(hvl.um()), layers);
        return new Length(Decimals.ceiling(micrometres));
    }

    /**
     * The barrier that brings an unshielded rate down to a target; none when it is already there.
     *
     * @throws ValidationError for a target that is not above zero
     * @throws ConfigurationError as {@link #halfValueLayer}
     */
    public static Length thicknessForRate(Isotope source, String material, DoseRate unshielded, DoseRate target) {
        if (target.nsvPerHour() <= 0) {
            throw new ValidationError("a target rate has to be positive");
        }
        if (unshielded.nsvPerHour() <= target.nsvPerHour()) {
            return Length.ZERO;
        }
        BigDecimal fraction = Decimals.divide(target.nsvPerHour(), unshielded.nsvPerHour());
        return thicknessForTransmission(source, material, fraction);
    }

    /** The materials the table publishes layers for with that isotope, sorted; none for an isotope it lacks. */
    public static List<String> materialsFor(Isotope source) {
        return List.copyOf(
                HALF_VALUE_LAYERS.getOrDefault(source.key(), Map.of()).keySet());
    }

    /**
     * Read the printed rows into the table.
     *
     * @throws ConfigurationError for a row without a cell under every material
     */
    private static Map<String, Map<String, Length>> table(String... rows) {
        Map<String, Map<String, Length>> table = new LinkedHashMap<>();
        for (String row : rows) {
            String[] cells = row.trim().split("\\s+");
            if (cells.length != SHIELD_MATERIALS.size() + 1) {
                throw new ConfigurationError(
                        "a shielding row has the wrong number of columns", Context.of("row", cells[0]));
            }
            Map<String, Length> layers = new TreeMap<>();
            for (int i = 0; i < SHIELD_MATERIALS.size(); i++) {
                layers.put(SHIELD_MATERIALS.get(i), Length.fromMm(cells[i + 1]));
            }
            table.put(cells[0], Collections.unmodifiableMap(layers));
        }
        return Collections.unmodifiableMap(table);
    }
}
