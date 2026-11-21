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
 */
public final class Materials {

    private static final Map<String, Material> MATERIALS = new LinkedHashMap<>();

    static {
        add("magnesium", "magnesium", 1740, "KV100", "0.05", "KV150", "0.05", "KV220", "0.08");
        add(
                "aluminium",
                "aluminium",
                2700,
                "KV100",
                "0.08",
                "KV150",
                "0.12",
                "KV220",
                "0.18",
                "IR192",
                "0.35",
                "CO60",
                "0.35");
        add(
                "aluminium_2024",
                "aluminium 2024",
                2780,
                "KV100",
                "0.10",
                "KV150",
                "0.14",
                "KV220",
                "0.18",
                "IR192",
                "0.35",
                "CO60",
                "0.35");
        add(
                "titanium",
                "titanium",
                4510,
                "KV150",
                "0.54",
                "KV220",
                "0.54",
                "KV400",
                "0.71",
                "MV1",
                "0.9",
                "MV2",
                "0.9",
                "MV4_25",
                "0.9",
                "IR192",
                "0.9",
                "CO60",
                "0.9");
        addFlat("carbon_steel", "carbon steel", 7850, "1.0");
        addFlat("stainless_304", "304 stainless", 8000, "1.0");
        addFlat("stainless_316", "316 stainless", 8000, "1.0");
        add(
                "copper", "copper", 8960, "KV100", "1.5", "KV150", "1.6", "KV220", "1.4", "KV400", "1.4", "MV1", "1.1",
                "MV2", "1.1", "MV4_25", "1.0", "IR192", "1.1", "CO60", "1.1");
        add(
                "brass", "brass", 8500, "KV150", "1.4", "KV220", "1.3", "KV400", "1.3", "MV1", "1.2", "MV2", "1.2",
                "IR192", "1.1", "CO60", "1.0");
        addFlat("inconel", "inconel X", 8440, "1.3", "KV100", "1.4");
        add(
                "zirconium",
                "zirconium",
                6520,
                "KV100",
                "2.4",
                "KV150",
                "2.3",
                "KV220",
                "2.0",
                "KV400",
                "1.7",
                "MV1",
                "1.5",
                "MV2",
                "1.0",
                "MV4_25",
                "1.0",
                "IR192",
                "1.2",
                "CO60",
                "1.0");
        add(
                "lead", "lead", 11340, "KV100", "14.0", "KV150", "14.0", "KV220", "12.0", "MV1", "5.0", "MV2", "2.5",
                "MV4_25", "3.0", "IR192", "4.0", "CO60", "2.3");
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

    private static void add(String key, String name, int density, String... qualitiesAndFactors) {
        Map<Quality, BigDecimal> factors = new EnumMap<>(Quality.class);
        for (int i = 0; i < qualitiesAndFactors.length; i += 2) {
            factors.put(Quality.valueOf(qualitiesAndFactors[i]), new BigDecimal(qualitiesAndFactors[i + 1]));
        }
        MATERIALS.put(key, new Material(key, name, density, factors));
    }

    /** The same factor at every quality, with any exceptions named after it. */
    private static void addFlat(String key, String name, int density, String factor, String... exceptions) {
        Map<Quality, BigDecimal> factors = new EnumMap<>(Quality.class);
        for (Quality quality : Quality.values()) {
            factors.put(quality, new BigDecimal(factor));
        }
        for (int i = 0; i < exceptions.length; i += 2) {
            factors.put(Quality.valueOf(exceptions[i]), new BigDecimal(exceptions[i + 1]));
        }
        MATERIALS.put(key, new Material(key, name, density, factors));
    }
}
