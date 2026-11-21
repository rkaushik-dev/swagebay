package io.github.rkaushikdev.swagebay.materials;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A material the shop shoots, with what is needed to plan an exposure through it.
 *
 * <p>Every exposure chart in the office is drawn for carbon steel, so a thickness of anything else
 * is converted first: multiply by the radiographic equivalence factor for the material and the
 * beam quality, read the chart at that steel thickness, and shoot. The factors are the ASTM E94
 * table, and where the table has a hole the hole is reported rather than filled with a guess.
 *
 * @param key the key the material is filed under
 * @param name the name a welder would use
 * @param densityKgM3 the density, for the shielding side; it decides no factor
 * @param factors the published equivalence factor at each beam quality
 */
public record Material(String key, String name, int densityKgM3, Map<Quality, BigDecimal> factors) {

    private static final Set<String> FERROUS = Set.of("carbon_steel", "stainless_304", "stainless_316");

    /** A material; the factors are copied. */
    public Material {
        factors =
                Collections.unmodifiableMap(factors.isEmpty() ? new EnumMap<>(Quality.class) : new EnumMap<>(factors));
    }

    /**
     * The equivalence factor at that quality.
     *
     * @throws ConfigurationError where the published table has a hole
     */
    public BigDecimal factorAt(Quality quality) {
        BigDecimal factor = factors.get(quality);
        if (factor == null) {
            throw new ConfigurationError(
                    "no radiographic equivalence factor is published for this combination",
                    Context.of("material", key, "quality", quality.value()));
        }
        return factor;
    }

    /**
     * The steel thickness that attenuates like this thickness of this material.
     *
     * @throws ValidationError for a negative thickness, before the table is looked at
     */
    public Length steelEquivalent(Length thickness, Quality quality) {
        if (thickness.um() < 0) {
            throw new ValidationError("a thickness cannot be negative", Context.of("um", thickness.um()));
        }
        return thickness.times(factorAt(quality));
    }

    /** Whether the material is one of the steels. */
    public boolean isFerrous() {
        return FERROUS.contains(key);
    }

    /** The name a welder would use. */
    @Override
    public String toString() {
        return name;
    }
}
