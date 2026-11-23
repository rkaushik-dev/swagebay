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
 * thin wall and small bore and hardly anybody owns one. Thulium is softer still and so weak that a
 * crew can work it with a short rope, which is the reason anybody puts up with exposure times that
 * long.
 */
public final class Isotopes {

    private static final Map<String, Isotope> ISOTOPES = new LinkedHashMap<>();

    static {
        row("ir192", "iridium 192", "Ir-192  73.83    0.48    206   612   12  63   IR192");
        row("se75", "selenium 75", "Se-75   119.78   0.203   66    401   4   28   -");
        row("co60", "cobalt 60", "Co-60   1925.3   1.32    1173  1333  50  200  CO60");
        row("yb169", "ytterbium 169", "Yb-169  32.0     0.125   63    308   1   15   -");
        row("cs137", "caesium 137", "Cs-137  11012.0  0.33    662   662   25  100  -");
        row("tm170", "thulium 170", "Tm-170  128.6    0.0025  52    84    2   12   -");
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
        return Optional.ofNullable(ISOTOPES.get(key))
                .orElseThrow(() -> new ConfigurationError("no such isotope", Context.of("key", key)));
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

    /**
     * One row of the source data: the symbol, the half life in days, roentgen an hour at a metre
     * from a curie, the lowest and highest line in keV, the steel range in millimetres, then the E94
     * column or a dash where the standard publishes none.
     */
    private static void row(String key, String name, String cells) {
        String[] cell = cells.trim().split("\\s+");
        if (cell.length != 8) {
            throw new ConfigurationError("an isotope row has the wrong number of columns", Context.of("key", key));
        }
        ISOTOPES.put(
                key,
                new Isotope(
                        key,
                        name,
                        cell[0],
                        new BigDecimal(cell[1]),
                        new BigDecimal(cell[2]),
                        Integer.parseInt(cell[3]),
                        Integer.parseInt(cell[4]),
                        Length.fromMm(cell[5]),
                        Length.fromMm(cell[6]),
                        cell[7].equals("-") ? Optional.empty() : Optional.of(Quality.valueOf(cell[7]))));
    }
}
