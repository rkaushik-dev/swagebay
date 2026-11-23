package io.github.rkaushikdev.swagebay.materials;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The materials the shop shoots, with their ASTM E94 equivalence factors.
 *
 * <p>The factors are approximate by construction and a function of the beam quality, not just the
 * isotope. Two inches of aluminium at 150 kV behaves like a quarter inch of steel, which is why an
 * aluminium job takes a fraction of the time.
 *
 * <p>The table is kept the way the standard prints it, one row per material and a column per beam
 * quality, so checking it against a revision of E94 is reading across a row rather than counting
 * arguments. Zinc is in it for galvanised work, where the coating is thin but the castings are not.
 */
public final class Materials {

    /** The columns of the table, in the order E94 prints them. */
    public static final List<Quality> COLUMNS = List.of(Quality.values());

    private static final String NOT_PUBLISHED = "-";

    private static final Map<String, Material> MATERIALS = new LinkedHashMap<>();

    static {
        row("magnesium", "magnesium", 1740, "0.05  0.05  0.08  -     -    -    -      -      -");
        row("aluminium", "aluminium", 2700, "0.08  0.12  0.18  -     -    -    -      0.35   0.35");
        row("aluminium_2024", "aluminium 2024", 2780, "0.10  0.14  0.18  -     -    -    -      0.35   0.35");
        row("titanium", "titanium", 4510, "-     0.54  0.54  0.71  0.9  0.9  0.9    0.9    0.9");
        row("carbon_steel", "carbon steel", 7850, "1.0   1.0   1.0   1.0   1.0  1.0  1.0    1.0    1.0");
        row("stainless_304", "304 stainless", 8000, "1.0   1.0   1.0   1.0   1.0  1.0  1.0    1.0    1.0");
        row("stainless_316", "316 stainless", 8000, "1.0   1.0   1.0   1.0   1.0  1.0  1.0    1.0    1.0");
        row("copper", "copper", 8960, "1.5   1.6   1.4   1.4   1.1  1.1  1.0    1.1    1.1");
        row("zinc", "zinc", 7140, "-     1.4   1.3   1.3   -    1.1  1.0    1.1    1.0");
        row("brass", "brass", 8500, "-     1.4   1.3   1.3   1.2  1.2  -      1.1    1.0");
        row("inconel", "inconel X", 8440, "1.4   1.3   1.3   1.3   1.3  1.3  1.3    1.3    1.3");
        row("zirconium", "zirconium", 6520, "2.4   2.3   2.0   1.7   1.5  1.0  1.0    1.2    1.0");
        row("lead", "lead", 11340, "14.0  14.0  12.0  -     5.0  2.5  3.0    4.0    2.3");
    }

    /** Carbon steel, which every chart is drawn for. */
    public static final Material STEEL = MATERIALS.get("carbon_steel");

    private Materials() {}

    /**
     * Look a material up by its key.
     *
     * @throws ConfigurationError for anything not in the file
     */
    public static Material material(String key) {
        Material found = MATERIALS.get(key);
        if (found == null) {
            throw new ConfigurationError("no such material", Context.of("key", key));
        }
        return found;
    }

    /** Every key in the file, sorted. */
    public static List<String> knownMaterials() {
        return MATERIALS.keySet().stream().sorted().toList();
    }

    /**
     * One row of the table: a factor under every column, a dash where E94 has a hole.
     *
     * @throws ConfigurationError for a row that does not have a cell under every column
     */
    private static void row(String key, String name, int density, String cells) {
        String[] factors = cells.trim().split("\\s+");
        if (factors.length != COLUMNS.size()) {
            throw new ConfigurationError("an equivalence row has the wrong number of columns", Context.of("key", key));
        }
        Map<Quality, BigDecimal> published = new EnumMap<>(Quality.class);
        for (int i = 0; i < factors.length; i++) {
            if (!factors[i].equals(NOT_PUBLISHED)) {
                published.put(COLUMNS.get(i), new BigDecimal(factors[i]));
            }
        }
        MATERIALS.put(key, new Material(key, name, density, published));
    }
}
