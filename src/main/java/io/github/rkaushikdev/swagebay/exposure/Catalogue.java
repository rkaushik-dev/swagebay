package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The exposure charts this shop actually owns.
 *
 * <p>Seven curves, established by shooting step wedges and re-established the last time the
 * processor chemistry changed, which is why the density each was drawn at is part of its identity.
 * The D4 card is the newest, drawn for the fine grain work on thin wall where D7 is too coarse, and
 * it sits at the speed ratio from D7 that the film data sheets say it should.
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
            card(
                    "ir192-steel-d7-2.0 ir192 carbon_steel d7 2.0 24",
                    "0.25  0.375  0.50  0.625  0.75  0.875  1.00  1.25  1.50  1.75  2.00  2.25  2.50",
                    "4     5.6    8     11     16    22     30    55    100   180   320   580   1050"),
            card(
                    "ir192-steel-d5-2.0 ir192 carbon_steel d5 2.0 24",
                    "0.25  0.50  0.75  1.00  1.25  1.50  1.75  2.00",
                    "7.3   14.5  29    55    100   182   327   582"),
            card(
                    "ir192-steel-d4-2.0 ir192 carbon_steel d4 2.0 24",
                    "0.25  0.50  0.75  1.00  1.25  1.50  1.75  2.00",
                    "12.5  25    50    94    172   312   562   1000"),
            card(
                    "ir192-steel-d7-2.5 ir192 carbon_steel d7 2.5 24",
                    "0.25  0.50  0.75  1.00  1.25  1.50  1.75  2.00",
                    "5.4   10.8  21.6  40.5  74    135   243   432"),
            card(
                    "se75-steel-d7-2.0 se75 carbon_steel d7 2.0 24",
                    "0.16  0.25  0.375  0.50  0.625  0.75  0.875  1.00",
                    "6     11    22     44    88     175   350    700"),
            card(
                    "co60-steel-d7-2.0 co60 carbon_steel d7 2.0 36",
                    "2.00  2.50  3.00  3.50  4.00  5.00  6.00  7.00  8.00",
                    "60    88    130   190   280   600   1300  2800  6000"),
            card(
                    "ir192-aluminium-d7-2.0 ir192 aluminium d7 2.0 24",
                    "0.50  1.00  1.50  2.00  3.00  4.00",
                    "3.2   5.4   9.1   15.4  44    125"));

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
        return findChart(isotopeKey, materialKey, filmKey, density)
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

    /** The chart drawn for exactly that isotope, material, film and density, if the shop has one. */
    public static Optional<ExposureChart> findChart(
            String isotopeKey, String materialKey, String filmKey, BigDecimal density) {
        return chartsForFilmSubstitution(isotopeKey, materialKey, density).stream()
                .filter(candidate -> candidate.filmKey().equals(filmKey))
                .findFirst();
    }

    /** The charts that match everything but the film, so a speed factor can be applied to one. */
    public static List<ExposureChart> chartsForFilmSubstitution(
            String isotopeKey, String materialKey, BigDecimal density) {
        return CHARTS.stream()
                .filter(candidate -> candidate.isotopeKey().equals(isotopeKey))
                .filter(candidate -> candidate.materialKey().equals(materialKey))
                .filter(candidate -> candidate.density().compareTo(density) == 0)
                .toList();
    }

    /**
     * A chart the way the card on the wall is printed: a heading with the key, the isotope, the
     * material, the film, the density and the distance in inches, then the thicknesses in inches
     * across one row and the curie minutes under each of them in the next.
     *
     * @throws ConfigurationError for a heading that is not six fields, or rows of different lengths
     */
    private static ExposureChart card(String heading, String thicknessesInches, String curieMinutes) {
        String[] fields = heading.trim().split("\\s+");
        String[] thicknesses = thicknessesInches.trim().split("\\s+");
        String[] readings = curieMinutes.trim().split("\\s+");
        if (fields.length != 6 || thicknesses.length != readings.length) {
            throw new ConfigurationError("an exposure card is not laid out right", Context.of("heading", heading));
        }
        List<Map.Entry<String, String>> points = IntStream.range(0, thicknesses.length)
                .mapToObj(i -> Map.entry(thicknesses[i], readings[i]))
                .toList();
        return ExposureChart.fromInches(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], points);
    }
}
