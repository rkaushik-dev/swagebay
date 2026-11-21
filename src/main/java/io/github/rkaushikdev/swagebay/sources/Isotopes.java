package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The isotopes the shop shoots with.
 *
 * <p>Iridium is the workhorse and it covers most of what a pipeline crew meets. Selenium is softer
 * and is what gets used on thin wall where iridium washes the contrast out. Cobalt is for heavy
 * vessel wall and it needs a shielded area nobody has on a construction site. Ytterbium is for very
 * thin wall and small bore and hardly anybody owns one.
 */
public final class Isotopes {

    private static final Map<String, Isotope> ISOTOPES = new LinkedHashMap<>();

    static {
        add("ir192", "iridium 192", "Ir-192", "73.83", "0.48", 206, 612, 12, 63, Quality.IR192);
        add("se75", "selenium 75", "Se-75", "119.78", "0.203", 66, 401, 4, 28, null);
        add("co60", "cobalt 60", "Co-60", "1925.3", "1.32", 1173, 1333, 50, 200, Quality.CO60);
        add("yb169", "ytterbium 169", "Yb-169", "32.0", "0.125", 63, 308, 1, 15, null);
        add("cs137", "caesium 137", "Cs-137", "11012.0", "0.33", 662, 662, 25, 100, null);
    }

    /** Iridium 192, the workhorse. */
    public static final Isotope IR192 = ISOTOPES.get("ir192");

    /** Selenium 75, for thin wall. */
    public static final Isotope SE75 = ISOTOPES.get("se75");

    /** Cobalt 60, for heavy wall. */
    public static final Isotope CO60 = ISOTOPES.get("co60");

    private Isotopes() {}

    /**
     * Look an isotope up by its key.
     *
     * @throws ConfigurationError for an isotope the shop does not hold
     */
    public static Isotope isotope(String key) {
        Isotope found = ISOTOPES.get(key);
        if (found == null) {
            throw new ConfigurationError("no such isotope", Context.of("key", key));
        }
        return found;
    }

    /** Every isotope key, sorted. */
    public static List<String> knownIsotopes() {
        return ISOTOPES.keySet().stream().sorted().toList();
    }

    /** Every isotope whose practical range takes that wall, softest first. */
    public static List<Isotope> isotopesCovering(Length steelThickness) {
        return ISOTOPES.values().stream()
                .filter(candidate -> candidate.covers(steelThickness))
                .sorted(Comparator.comparingInt(Isotope::energyKevHigh))
                .toList();
    }

    private static void add(
            String key,
            String name,
            String symbol,
            String halfLifeDays,
            String gammaConstant,
            int energyLow,
            int energyHigh,
            long steelMinMm,
            long steelMaxMm,
            Quality quality) {
        ISOTOPES.put(
                key,
                new Isotope(
                        key,
                        name,
                        symbol,
                        new BigDecimal(halfLifeDays),
                        new BigDecimal(gammaConstant),
                        energyLow,
                        energyHigh,
                        Length.fromMm(steelMinMm),
                        Length.fromMm(steelMaxMm),
                        Optional.ofNullable(quality)));
    }
}
