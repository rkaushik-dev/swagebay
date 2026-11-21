package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.imaging.FilmSystem;
import io.github.rkaushikdev.swagebay.materials.Material;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * The corrections applied to a chart reading.
 *
 * <p>A chart gives curie minutes for one distance, one film and one density, and a real shot is
 * almost never all three, so the reading is multiplied by a factor for each thing that differs.
 * The correction for shooting further away multiplies and the correction for a faster film divides,
 * and both feel like they should go the other way, which is why each one is built here once.
 */
public final class Corrections {

    private static final BigDecimal SCREEN_FACTOR = new BigDecimal("0.5");

    private Corrections() {}

    /**
     * Further away needs longer, by the square of the ratio of the distances.
     *
     * @throws ValidationError unless both distances are positive
     */
    public static Correction distanceCorrection(Length chartSfd, Length actualSfd) {
        if (chartSfd.um() <= 0 || actualSfd.um() <= 0) {
            throw new ValidationError("both distances have to be positive");
        }
        BigDecimal ratio = actualSfd.ratio(chartSfd);
        return new Correction(
                "distance",
                Decimals.multiply(ratio, ratio),
                "chart at " + chartSfd.formatInch(0) + ", shot at " + actualSfd.formatInch(0));
    }

    /** A faster film needs less time, so the factor is the speed ratio inverted. */
    public static Correction filmCorrection(FilmSystem chartFilm, FilmSystem actualFilm) {
        return new Correction(
                "film",
                actualFilm.timeFactorFrom(chartFilm),
                "chart on " + chartFilm.name() + ", shot on " + actualFilm.name());
    }

    /**
     * When the chart is steel and the weld is not.
     *
     * <p>This one is applied to the thickness before the chart is read, not to the exposure after.
     * It is built here so the sheet can print it in the same chain, and the factor is reported
     * rather than folded in, so nobody applies it twice.
     *
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError where the equivalence table has a hole
     * @throws ValidationError for a chart material without a positive factor
     */
    public static Correction materialCorrection(Material chartMaterial, Material actualMaterial, Quality quality) {
        BigDecimal chartFactor = chartMaterial.factorAt(quality);
        BigDecimal actualFactor = actualMaterial.factorAt(quality);
        if (chartFactor.signum() <= 0) {
            throw new ValidationError("a chart material needs a positive equivalence factor");
        }
        return new Correction(
                "material",
                Decimals.divide(actualFactor, chartFactor),
                actualMaterial + " read as " + chartMaterial + " at " + quality);
    }

    /** Lead screens intensify, so a pair fitted halves the shot. */
    public static Correction screenCorrection(boolean present) {
        return screenCorrection(present, SCREEN_FACTOR);
    }

    /** Lead screens that shorten the shot by that factor, or none fitted. */
    public static Correction screenCorrection(boolean present, BigDecimal factor) {
        return new Correction(
                "screens", present ? factor : BigDecimal.ONE, present ? "lead screens fitted" : "no screens");
    }

    /**
     * A collimator that clips the beam edge costs a little exposure.
     *
     * @throws ValidationError for a transmission outside (0, 1]
     */
    public static Correction collimatorCorrection(BigDecimal transmission) {
        if (transmission.signum() <= 0 || transmission.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError("collimator transmission sits in (0, 1]");
        }
        return new Correction(
                "collimator",
                Decimals.divide(BigDecimal.ONE, transmission),
                "beam transmission " + Decimals.normalize(transmission));
    }
}
