package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An exposure chart for an x-ray set: a family of curves, one per kilovoltage, valid for one
 * material, film, density and distance.
 *
 * <p>An isotope chart is one curve. A tube chart is a family of them and the shooter picks the
 * curve before reading it. Reading is logarithmic along a curve and refused between curves: a
 * technique at 165 kilovolts is real on a set with a continuous control, but reading it by
 * averaging the 150 and 180 curves is not, because the curves are not evenly spaced and the family
 * is not linear in kilovoltage. The nearest established curve can be asked for explicitly.
 *
 * @param key the key the chart is filed under
 * @param materialKey the material it was drawn for
 * @param filmKey the film it was drawn for
 * @param density the density it was drawn for
 * @param referenceSfd the source to film distance it was drawn at
 * @param curves the curves, one per kilovoltage
 */
public record TubeChart(
        String key, String materialKey, String filmKey, BigDecimal density, Length referenceSfd, List<Curve> curves) {

    /**
     * A chart; the curves are copied.
     *
     * @throws ConfigurationError for a chart with no curve or two at one kilovoltage
     */
    public TubeChart {
        curves = List.copyOf(curves);
        if (curves.isEmpty()) {
            throw new ConfigurationError("a chart has at least one curve", Context.of("key", key));
        }
        if (new HashSet<>(curves.stream().map(Curve::kv).toList()).size() != curves.size()) {
            throw new ConfigurationError("a chart has two curves at one kilovoltage", Context.of("key", key));
        }
    }

    /** The kilovoltages there are curves for, lowest first. */
    public List<Integer> kilovoltages() {
        return curves.stream().map(Curve::kv).sorted().toList();
    }

    /**
     * The curve at exactly that kilovoltage.
     *
     * @throws TechniqueError naming the kilovoltages there are curves for
     */
    public Curve curve(int kv) {
        return curves.stream()
                .filter(candidate -> candidate.kv() == kv)
                .findFirst()
                .orElseThrow(() -> new TechniqueError(
                        "no curve is established at that kilovoltage",
                        Context.of(
                                "key",
                                key,
                                "kv",
                                kv,
                                "established",
                                kilovoltages().stream().map(String::valueOf).collect(Collectors.joining(", ")))));
    }

    /** The established curve closest to a wanted kilovoltage, the harder one on a tie. */
    public int nearestKilovoltage(int kv) {
        return kilovoltages().stream()
                .min(Comparator.<Integer>comparingInt(established -> Math.abs(established - kv))
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    /**
     * The milliampere seconds at a kilovoltage and a thickness.
     *
     * @throws TechniqueError for a kilovoltage with no curve or a thickness off the curve
     */
    public BigDecimal masFor(int kv, Length thickness) {
        return curve(kv).masFor(thickness);
    }
}
