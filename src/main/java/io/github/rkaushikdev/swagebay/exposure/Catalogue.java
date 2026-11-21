package io.github.rkaushikdev.swagebay.exposure;

import static java.util.Map.entry;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import java.math.BigDecimal;
import java.util.List;

/**
 * The exposure charts this shop actually owns.
 *
 * <p>Six curves, established by shooting step wedges and re-established the last time the
 * processor chemistry changed, which is why the density each was drawn at is part of its identity.
 *
 * <p>The resolver is deliberately narrow. Ask for an isotope, a material, a film and a density and
 * either a chart exists or it does not. There is no nearest match and no averaging of two curves,
 * because a chart is an empirical object and the average of two empirical objects is not one. What
 * it does allow is a chart drawn for a different film, returned with the film it was drawn for
 * named, so the caller can apply the speed factor knowingly. What it will not do is silently
 * substitute a density, because density corrections are where the argument with the client starts.
 */
public final class Catalogue {

    private static final List<ExposureChart> CHARTS = List.of(
            ExposureChart.fromInches(
                    "ir192-steel-d7-2.0",
                    "ir192",
                    "carbon_steel",
                    "d7",
                    "2.0",
                    "24",
                    List.of(
                            entry("0.25", "4"),
                            entry("0.375", "5.6"),
                            entry("0.50", "8"),
                            entry("0.625", "11"),
                            entry("0.75", "16"),
                            entry("0.875", "22"),
                            entry("1.00", "30"),
                            entry("1.25", "55"),
                            entry("1.50", "100"),
                            entry("1.75", "180"),
                            entry("2.00", "320"),
                            entry("2.25", "580"),
                            entry("2.50", "1050"))),
            ExposureChart.fromInches(
                    "ir192-steel-d5-2.0",
                    "ir192",
                    "carbon_steel",
                    "d5",
                    "2.0",
                    "24",
                    List.of(
                            entry("0.25", "7.3"),
                            entry("0.50", "14.5"),
                            entry("0.75", "29"),
                            entry("1.00", "55"),
                            entry("1.25", "100"),
                            entry("1.50", "182"),
                            entry("1.75", "327"),
                            entry("2.00", "582"))),
            ExposureChart.fromInches(
                    "ir192-steel-d7-2.5",
                    "ir192",
                    "carbon_steel",
                    "d7",
                    "2.5",
                    "24",
                    List.of(
                            entry("0.25", "5.4"),
                            entry("0.50", "10.8"),
                            entry("0.75", "21.6"),
                            entry("1.00", "40.5"),
                            entry("1.25", "74"),
                            entry("1.50", "135"),
                            entry("1.75", "243"),
                            entry("2.00", "432"))),
            ExposureChart.fromInches(
                    "se75-steel-d7-2.0",
                    "se75",
                    "carbon_steel",
                    "d7",
                    "2.0",
                    "24",
                    List.of(
                            entry("0.16", "6"),
                            entry("0.25", "11"),
                            entry("0.375", "22"),
                            entry("0.50", "44"),
                            entry("0.625", "88"),
                            entry("0.75", "175"),
                            entry("0.875", "350"),
                            entry("1.00", "700"))),
            ExposureChart.fromInches(
                    "co60-steel-d7-2.0",
                    "co60",
                    "carbon_steel",
                    "d7",
                    "2.0",
                    "36",
                    List.of(
                            entry("2.00", "60"),
                            entry("2.50", "88"),
                            entry("3.00", "130"),
                            entry("3.50", "190"),
                            entry("4.00", "280"),
                            entry("5.00", "600"),
                            entry("6.00", "1300"),
                            entry("7.00", "2800"),
                            entry("8.00", "6000"))),
            ExposureChart.fromInches(
                    "ir192-aluminium-d7-2.0",
                    "ir192",
                    "aluminium",
                    "d7",
                    "2.0",
                    "24",
                    List.of(
                            entry("0.50", "3.2"),
                            entry("1.00", "5.4"),
                            entry("1.50", "9.1"),
                            entry("2.00", "15.4"),
                            entry("3.00", "44"),
                            entry("4.00", "125"))));

    private Catalogue() {}

    /** Every chart the shop owns. */
    public static List<ExposureChart> allCharts() {
        return CHARTS;
    }

    /** Every chart key, sorted. */
    public static List<String> chartKeys() {
        return CHARTS.stream().map(ExposureChart::key).sorted().toList();
    }

    /**
     * Look a chart up by its key.
     *
     * @throws ConfigurationError for a chart the shop does not own
     */
    public static ExposureChart chart(String key) {
        return CHARTS.stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new ConfigurationError("no such exposure chart", Context.of("key", key)));
    }

    /**
     * The chart drawn for exactly that isotope, material, film and density.
     *
     * @throws ConfigurationError naming all four when no such chart has been established
     */
    public static ExposureChart chartFor(String isotopeKey, String materialKey, String filmKey, BigDecimal density) {
        return CHARTS.stream()
                .filter(candidate -> candidate.isotopeKey().equals(isotopeKey)
                        && candidate.materialKey().equals(materialKey)
                        && candidate.filmKey().equals(filmKey)
                        && candidate.density().compareTo(density) == 0)
                .findFirst()
                .orElseThrow(() -> new ConfigurationError(
                        "no chart has been established for that combination",
                        Context.of(
                                "isotope",
                                isotopeKey,
                                "material",
                                materialKey,
                                "film",
                                filmKey,
                                "density",
                                density.toString())));
    }

    /** The charts that match everything but the film, so a speed factor can be applied to one. */
    public static List<ExposureChart> chartsForFilmSubstitution(
            String isotopeKey, String materialKey, BigDecimal density) {
        return CHARTS.stream()
                .filter(candidate -> candidate.isotopeKey().equals(isotopeKey)
                        && candidate.materialKey().equals(materialKey)
                        && candidate.density().compareTo(density) == 0)
                .toList();
    }
}
