package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The films the shop stocks, and the density the code demands of them.
 *
 * <p>After the shot, density is the gate. Too thin and there is no contrast to read; too dense and
 * the viewer cannot push enough light through. ASME puts the floor at 1.8 for x-ray and 2.0 for
 * gamma and the ceiling at 4.0 for both, and those numbers are hard. The variation rule is softer
 * and more often missed: through the body of the weld the density may not fall below 85 percent or
 * rise above 130 percent of the density read through the indicator.
 */
public final class Films {

    /** The lowest density read on an x-ray film. */
    public static final BigDecimal DENSITY_FLOOR_XRAY = new BigDecimal("1.8");

    /** The lowest density read on a gamma film. */
    public static final BigDecimal DENSITY_FLOOR_GAMMA = new BigDecimal("2.0");

    /** The highest density read on any film. */
    public static final BigDecimal DENSITY_CEILING = new BigDecimal("4.0");

    /** The lowest density of each film in a composite viewing. */
    public static final BigDecimal DENSITY_FLOOR_COMPOSITE = new BigDecimal("1.3");

    /** The weld may read no thinner than this share of the indicator density. */
    public static final BigDecimal VARIATION_LOW = new BigDecimal("0.85");

    /** The weld may read no denser than this share of the indicator density. */
    public static final BigDecimal VARIATION_HIGH = new BigDecimal("1.30");

    private static final Map<String, FilmSystem> FILMS = new LinkedHashMap<>();

    static {
        add("d2", "Structurix D2", "Agfa", FilmClass.SPECIAL, "6", "0.012");
        add("d3", "Structurix D3", "Agfa", FilmClass.CLASS_I, "15", "0.014");
        add("d4", "Structurix D4", "Agfa", FilmClass.CLASS_I, "32", "0.017");
        add("d5", "Structurix D5", "Agfa", FilmClass.CLASS_II, "55", "0.019");
        add("d7", "Structurix D7", "Agfa", FilmClass.CLASS_II, "100", "0.024");
        add("d8", "Structurix D8", "Agfa", FilmClass.CLASS_III, "180", "0.032");
        add("mx125", "Industrex MX125", "Carestream", FilmClass.CLASS_I, "20", "0.013");
        add("t200", "Industrex T200", "Carestream", FilmClass.CLASS_I, "40", "0.018");
        add("aa400", "Industrex AA400", "Carestream", FilmClass.CLASS_II, "100", "0.023");
        add("hs800", "Industrex HS800", "Carestream", FilmClass.CLASS_III, "200", "0.035");
    }

    /** The film every exposure chart on the wall is drawn for. */
    public static final FilmSystem REFERENCE_FILM = FILMS.get("d7");

    /** The band the density through the weld has to stay inside, both ends included. */
    public record Window(BigDecimal low, BigDecimal high) {}

    private Films() {}

    /**
     * Look a film system up by its key.
     *
     * @throws ConfigurationError for a film the shop does not stock
     */
    public static FilmSystem film(String key) {
        FilmSystem found = FILMS.get(key);
        if (found == null) {
            throw new ConfigurationError("no such film system", Context.of("key", key));
        }
        return found;
    }

    /** Every film key, sorted. */
    public static List<String> knownFilms() {
        return FILMS.keySet().stream().sorted().toList();
    }

    /** The films in a class, slowest first. */
    public static List<FilmSystem> filmsInClass(FilmClass filmClass) {
        return FILMS.values().stream()
                .filter(f -> f.filmClass() == filmClass)
                .sorted(Comparator.comparing(FilmSystem::speedIndex))
                .toList();
    }

    /** The lowest density the code will read, which depends on the source. */
    public static BigDecimal densityFloor(boolean gamma, boolean composite) {
        if (composite) {
            return DENSITY_FLOOR_COMPOSITE;
        }
        return gamma ? DENSITY_FLOOR_GAMMA : DENSITY_FLOOR_XRAY;
    }

    /** The density floor for a single film. */
    public static BigDecimal densityFloor(boolean gamma) {
        return densityFloor(gamma, false);
    }

    /** Whether a density is between the floor and the ceiling, both included. */
    public static boolean densityInRange(BigDecimal density, boolean gamma, boolean composite) {
        return densityFloor(gamma, composite).compareTo(density) <= 0 && density.compareTo(DENSITY_CEILING) <= 0;
    }

    /** Whether a single film's density is in range. */
    public static boolean densityInRange(BigDecimal density, boolean gamma) {
        return densityInRange(density, gamma, false);
    }

    /**
     * The band the density through the weld has to stay inside.
     *
     * @throws ValidationError for a reading that is not positive
     */
    public static Window variationWindow(BigDecimal throughIndicator) {
        if (throughIndicator.signum() <= 0) {
            throw new ValidationError("a density reading has to be positive");
        }
        return new Window(
                Decimals.multiply(throughIndicator, VARIATION_LOW),
                Decimals.multiply(throughIndicator, VARIATION_HIGH));
    }

    /** Whether the weld density is inside the variation band of the indicator density. */
    public static boolean variationAcceptable(BigDecimal throughIndicator, BigDecimal throughWeld) {
        Window window = variationWindow(throughIndicator);
        return window.low().compareTo(throughWeld) <= 0 && throughWeld.compareTo(window.high()) <= 0;
    }

    private static void add(String key, String name, String maker, FilmClass filmClass, String speed, String grain) {
        FILMS.put(key, new FilmSystem(key, name, maker, filmClass, new BigDecimal(speed), new BigDecimal(grain)));
    }
}
