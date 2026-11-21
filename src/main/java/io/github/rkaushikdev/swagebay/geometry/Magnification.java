package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Deliberate magnification, the one technique that gets better by moving the film away.
 *
 * <p>Everything else wants the film hard against the part, because an air gap magnifies the
 * geometric unsharpness. Magnification technique does the opposite on purpose: pull the film back,
 * let the image grow, and read a flaw that was smaller than the detector could resolve. It only
 * works with a very small focal spot, because the image grows by the magnification and so does the
 * geometric unsharpness.
 *
 * <p>The optimum is derived from this package's own model rather than quoted. The total
 * unsharpness referred back to the part, with the two terms added in quadrature the way the duplex
 * gauge measures them, is {@code U(M) = sqrt((F(M-1))^2 + Ud^2) / M}, and differentiating that gives
 * the minimum at {@code M = 1 + (Ud / F)^2}. Textbooks quote other exponents because they minimise
 * something else; quoting one of those beside a quadrature model would give a number that does not
 * minimise the expression it sits next to.
 *
 * <p>The derived optimum runs away at high ratios, so there is a practical ceiling as well: output
 * falls with the square of the distance, and a magnification of fifty turns a two minute shot into
 * an hour. On a three millimetre iridium pellet the optimum is barely above one, which this says
 * rather than computing a number nobody should use.
 */
public final class Magnification {

    /** Above this focal size magnification buys nothing worth having. */
    public static final Length MICROFOCUS_CEILING = Length.fromMm("0.4");

    /** Below this the film may as well be against the part. */
    public static final BigDecimal USEFUL_MAGNIFICATION = new BigDecimal("1.2");

    /** Above this the exposure time makes the technique academic. */
    public static final BigDecimal PRACTICAL_CEILING = BigDecimal.valueOf(20);

    private Magnification() {}

    /**
     * A magnification geometry, described by its two distances, the spot and the detector.
     *
     * @param sourceToObject from the source to the part, positive
     * @param objectToFilm the air gap from the part to the detector, not negative
     * @param focalSize the focal spot, positive
     * @param detectorUnsharpness the detector's own blur, positive
     */
    public record Setup(Length sourceToObject, Length objectToFilm, Length focalSize, Length detectorUnsharpness) {

        /**
         * A setup.
         *
         * @throws ValidationError for a distance, spot or detector blur that is not positive, or a
         *     negative gap
         */
        public Setup {
            Objects.requireNonNull(sourceToObject, "sourceToObject");
            Objects.requireNonNull(objectToFilm, "objectToFilm");
            Objects.requireNonNull(focalSize, "focalSize");
            Objects.requireNonNull(detectorUnsharpness, "detectorUnsharpness");
            if (sourceToObject.um() <= 0) {
                throw new ValidationError("the source stands off the part");
            }
            if (objectToFilm.um() < 0) {
                throw new ValidationError("an air gap is not negative");
            }
            if (focalSize.um() <= 0) {
                throw new ValidationError("a focal spot has a size");
            }
            if (detectorUnsharpness.um() <= 0) {
                throw new ValidationError("a detector has an unsharpness");
            }
        }

        /** Source to detector over source to part. */
        public BigDecimal magnification() {
            return sourceToObject.plus(objectToFilm).ratio(sourceToObject);
        }

        /** The focal spot scaled by the gap over the source distance, to the nearest micrometre. */
        public Length geometricUnsharpness() {
            return new Length(BeamGeometry.unsharpnessUm(focalSize, objectToFilm, sourceToObject));
        }

        /** The detector blur as it appears back on the part, shrunk by the magnification, cut to the micrometre. */
        public Length effectiveDetectorUnsharpness() {
            return new Length(
                    Decimals.truncate(Decimals.divide(BigDecimal.valueOf(detectorUnsharpness.um()), magnification())));
        }

        /** The two terms in quadrature, referred back to the part, cut to the micrometre. */
        public Length totalUnsharpness() {
            BigDecimal geometric =
                    Decimals.divide(BigDecimal.valueOf(geometricUnsharpness().um()), magnification());
            BigDecimal detector =
                    BigDecimal.valueOf(effectiveDetectorUnsharpness().um());
            BigDecimal squares =
                    Decimals.add(Decimals.multiply(geometric, geometric), Decimals.multiply(detector, detector));
            return new Length(Decimals.truncate(Decimals.sqrt(squares)));
        }

        /** Whether the magnification reaches {@link #USEFUL_MAGNIFICATION}. */
        public boolean isWorthDoing() {
            return magnification().compareTo(USEFUL_MAGNIFICATION) >= 0;
        }
    }

    /**
     * A fixed source to film distance split to reach a magnification.
     *
     * @param sourceToObject from the source to the part
     * @param objectToFilm from the part to the film
     */
    public record Distances(Length sourceToObject, Length objectToFilm) {}

    /**
     * Where the model says the object referred blur is least: {@code 1 + (Ud / F)^2}.
     *
     * @throws ValidationError when either term is not positive
     * @throws TechniqueError for a focal spot over {@link #MICROFOCUS_CEILING}
     */
    public static BigDecimal theoreticalOptimum(Length focalSize, Length detectorUnsharpness) {
        if (focalSize.um() <= 0 || detectorUnsharpness.um() <= 0) {
            throw new ValidationError("both unsharpness terms have to be positive");
        }
        if (focalSize.compareTo(MICROFOCUS_CEILING) > 0) {
            throw new TechniqueError(
                    "magnification needs a microfocus source",
                    Context.of(
                            "focal_size_mm", focalSize.mm().toString(),
                            "ceiling_mm", MICROFOCUS_CEILING.mm().toString()));
        }
        BigDecimal ratio =
                Decimals.divide(BigDecimal.valueOf(detectorUnsharpness.um()), BigDecimal.valueOf(focalSize.um()));
        return Decimals.add(BigDecimal.ONE, Decimals.multiply(ratio, ratio));
    }

    /** The optimum a shooter can actually use, capped at {@link #PRACTICAL_CEILING}. */
    public static BigDecimal optimumMagnification(Length focalSize, Length detectorUnsharpness) {
        BigDecimal best = theoreticalOptimum(focalSize, detectorUnsharpness);
        return best.compareTo(PRACTICAL_CEILING) > 0 ? PRACTICAL_CEILING : best;
    }

    /**
     * Split a fixed source to film distance to reach a wanted magnification; the source distance
     * is cut to the micrometre and the gap takes the rest.
     *
     * @throws ValidationError for a magnification under one or a distance that is not positive
     */
    public static Distances distancesFor(BigDecimal magnification, Length sourceToFilm) {
        if (magnification.compareTo(BigDecimal.ONE) < 0) {
            throw new ValidationError("a magnification is at least one");
        }
        if (sourceToFilm.um() <= 0) {
            throw new ValidationError("a source to film distance has to be positive");
        }
        Length sod =
                new Length(Decimals.truncate(Decimals.divide(BigDecimal.valueOf(sourceToFilm.um()), magnification)));
        return new Distances(sod, new Length(sourceToFilm.um() - sod.um()));
    }

    /** Whether the focal spot is at or under {@link #MICROFOCUS_CEILING}. */
    public static boolean isMicrofocus(Length focalSize) {
        return focalSize.compareTo(MICROFOCUS_CEILING) <= 0;
    }
}
