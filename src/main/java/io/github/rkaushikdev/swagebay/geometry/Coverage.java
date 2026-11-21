package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import java.math.BigDecimal;

/**
 * How much of a circumference one exposure can honestly carry.
 *
 * <p>The code minimum is three exposures for a double wall single image shot and that is a floor,
 * not an answer. Whether three is enough depends on how far back the source is, because the beam
 * only crosses the wall square on at the middle of the film. Everywhere else it crosses at an angle
 * and the path through the steel is longer, so the image is darker, the contrast is worse, and past
 * some angle the weld at the edge of the film is not being examined at all.
 *
 * <p>The path through the wall goes as one over the cosine of the angle between the ray and the
 * wall normal. That angle is worked out from the actual geometry rather than from a rule of thumb:
 * source at a known distance from a pipe of known radius, and a point on the weld a known angle
 * round from the beam axis.
 *
 * <p>The office allows the path at the edge of the diagnostic length to run ten percent longer than
 * the path at the middle. That number is a policy and it is an argument the shop has had with
 * clients more than once, so it is an argument here rather than a constant.
 *
 * <p>Trigonometry is done in binary floating point, through {@link StrictMath} so every machine
 * gets the same digits. It is the one place where that is the right call: the inputs are tape
 * measurements good to a millimetre, the output is a count of exposures, and no amount of decimal
 * precision changes an answer that gets rounded up to a whole film.
 */
public final class Coverage {

    /** The path at the edge of the film may run ten percent over the middle. */
    public static final BigDecimal DEFAULT_PATH_ALLOWANCE = new BigDecimal("1.10");

    /** Past this the guide tube will not reach and the exposure is absurd. */
    public static final Length STANDOFF_CEILING = Length.fromM(5);

    private static final BigDecimal FULL_CIRCLE = BigDecimal.valueOf(360);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final double RADIANS_PER_DEGREE = Math.PI / 180.0;
    private static final double DEGREES_PER_RADIAN = 180.0 / Math.PI;
    private static final int ARC_BISECTIONS = 60;
    private static final int STANDOFF_BISECTIONS = 50;
    private static final long STANDOFF_FLOOR_UM = 1000;

    private Coverage() {}

    /**
     * The angle between the ray and the wall normal, in degrees.
     *
     * <p>Zero at the middle of the film where the beam is square on, and it opens up as the point of
     * interest moves round the pipe away from the beam axis.
     *
     * @param radius the pipe's outside radius
     * @param sourceToSurface how far the source stands off the pipe
     * @param phiDegrees how far round the pipe the point is from the beam axis
     * @throws ValidationError for a radius or a standoff that is not positive
     */
    public static double incidenceAngle(Length radius, Length sourceToSurface, double phiDegrees) {
        if (radius.um() <= 0) {
            throw new ValidationError("a pipe has a radius");
        }
        if (sourceToSurface.um() <= 0) {
            throw new ValidationError("the source stands off the pipe");
        }
        long r = radius.um();
        long d = r + sourceToSurface.um();
        double phi = phiDegrees * RADIANS_PER_DEGREE;
        double chord = StrictMath.sqrt((double) (d * d + r * r) - (double) (2 * d * r) * StrictMath.cos(phi));
        if (chord == 0) {
            return 0.0;
        }
        double cosine = ((double) (r * r) + chord * chord - (double) (d * d)) / ((double) (2 * r) * chord);
        cosine = Math.max(-1.0, Math.min(1.0, cosine));
        return 180.0 - StrictMath.acos(cosine) * DEGREES_PER_RADIAN;
    }

    /**
     * How much longer the path through the wall is than at the middle, to six places.
     *
     * @throws TechniqueError when the beam grazes the wall at that angle
     */
    public static BigDecimal pathFactor(Length radius, Length sourceToSurface, double phiDegrees) {
        double angle = incidenceAngle(radius, sourceToSurface, phiDegrees);
        double cosine = StrictMath.cos(angle * RADIANS_PER_DEGREE);
        if (cosine <= 0) {
            throw new TechniqueError(
                    "the beam is grazing the wall at that angle",
                    Context.of("phi_degrees", phiDegrees, "incidence_degrees", Floats.round(angle, 2)));
        }
        return Floats.decimal(Floats.round(1.0 / cosine, 6));
    }

    /** The half arc either side of the beam axis inside {@link #DEFAULT_PATH_ALLOWANCE}. */
    public static BigDecimal usableHalfArc(Joint joint, Length sourceToSurface) {
        return usableHalfArc(joint, sourceToSurface, DEFAULT_PATH_ALLOWANCE);
    }

    /**
     * The half arc either side of the beam axis that stays inside the allowance, in degrees to three
     * places, found by bisection.
     *
     * @throws ValidationError for an allowance of one or less
     */
    public static BigDecimal usableHalfArc(Joint joint, Length sourceToSurface, BigDecimal allowance) {
        if (allowance.compareTo(BigDecimal.ONE) <= 0) {
            throw new ValidationError("an allowance over the middle path is greater than one");
        }
        Length radius = joint.od().dividedBy(2);
        double low = 0.0;
        double high = 180.0;
        for (int round = 0; round < ARC_BISECTIONS; round++) {
            double middle = (low + high) / 2;
            BigDecimal factor;
            try {
                factor = pathFactor(radius, sourceToSurface, middle);
            } catch (TechniqueError grazing) {
                high = middle;
                continue;
            }
            if (factor.compareTo(allowance) <= 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return Floats.decimal(Floats.round(low, 3));
    }

    /** The exposure count geometry demands under {@link #DEFAULT_PATH_ALLOWANCE}. */
    public static CoveragePlan planCoverage(Joint joint, Length sourceToSurface, int minimumExposures) {
        return planCoverage(joint, sourceToSurface, minimumExposures, DEFAULT_PATH_ALLOWANCE);
    }

    /**
     * The exposure count geometry demands, never below the code minimum.
     *
     * @throws ValidationError for a code minimum under one
     * @throws TechniqueError when no part of the weld is inside the allowance at that standoff
     */
    public static CoveragePlan planCoverage(
            Joint joint, Length sourceToSurface, int minimumExposures, BigDecimal allowance) {
        if (minimumExposures <= 0) {
            throw new ValidationError("the code minimum is at least one exposure");
        }
        BigDecimal half = usableHalfArc(joint, sourceToSurface, allowance);
        if (half.signum() <= 0) {
            throw new TechniqueError(
                    "no part of the weld is inside the path allowance at that distance",
                    Context.of("standoff_mm", sourceToSurface.mm().toString()));
        }
        BigDecimal perExposure = Decimals.multiply(half, TWO);
        int needed = Math.toIntExact(Decimals.ceiling(Decimals.divide(FULL_CIRCLE, perExposure)));
        int exposures = Math.max(needed, minimumExposures);
        BigDecimal separation = Decimals.divide(FULL_CIRCLE, exposures);
        return new CoveragePlan(exposures, half, separation, Decimals.subtract(perExposure, separation));
    }

    /** How far back the source has to go, under {@link #DEFAULT_PATH_ALLOWANCE}. */
    public static Length standoffForExposures(Joint joint, int exposures) {
        return standoffForExposures(joint, exposures, DEFAULT_PATH_ALLOWANCE);
    }

    /** How far back the source has to go, no further than {@link #STANDOFF_CEILING}. */
    public static Length standoffForExposures(Joint joint, int exposures, BigDecimal allowance) {
        return standoffForExposures(joint, exposures, allowance, STANDOFF_CEILING);
    }

    /**
     * How far back the source has to go for that many exposures to cover the joint, found by
     * bisection between a millimetre and the ceiling, to the micrometre.
     *
     * @throws ValidationError for fewer than one exposure
     * @throws TechniqueError when that many exposures will not cover the joint even at the ceiling
     */
    public static Length standoffForExposures(Joint joint, int exposures, BigDecimal allowance, Length ceiling) {
        if (exposures <= 0) {
            throw new ValidationError("a joint needs at least one exposure");
        }
        BigDecimal required = Decimals.divide(Decimals.divide(FULL_CIRCLE, exposures), TWO);
        long low = STANDOFF_FLOOR_UM;
        long high = ceiling.um();
        if (usableHalfArc(joint, new Length(high), allowance).compareTo(required) < 0) {
            throw new TechniqueError(
                    "that many exposures will not cover the joint even at the ceiling",
                    Context.of(
                            "exposures", exposures, "ceiling_mm", ceiling.mm().toString()));
        }
        for (int round = 0; round < STANDOFF_BISECTIONS; round++) {
            long middle = Math.floorDiv(low + high, 2);
            if (usableHalfArc(joint, new Length(middle), allowance).compareTo(required) >= 0) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return new Length(high);
    }
}
