package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.Collimator;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.sources.Output;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The radiation field round a shot: a source, what is around it, and what that leaves at a distance.
 *
 * <p>Everything on this side of the package answers one question: how far back does everybody have
 * to be. The field is the source rate, cut down by whatever is between the source and the person. A
 * collimator cuts it by a published fraction outside its aperture and a barrier by half value
 * layers. Both apply, the collimator first, which matters because a collimator is directional and a
 * barrier is not.
 *
 * <p>Every question takes a bearing off the beam axis in degrees, because the collimated and
 * uncollimated answers differ by a factor of twenty and getting it backwards puts the rope in the
 * wrong place. The model will not pretend a bearing it was not given.
 *
 * @param isotope the source isotope
 * @param activity its activity on the day
 * @param collimator the collimator fitted, if one is
 * @param barriers the barriers between the source and everybody, in any order
 */
public record RadiationField(
        Isotope isotope, Activity activity, Optional<Collimator> collimator, List<Barrier> barriers) {

    private static final BigDecimal UM_PER_M = BigDecimal.valueOf(1_000_000);

    /** A field; the barriers are copied. */
    public RadiationField {
        Objects.requireNonNull(isotope, "isotope");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(collimator, "collimator");
        barriers = List.copyOf(barriers);
    }

    /** A bare source, with nothing fitted and nothing in the way. */
    public RadiationField(Isotope isotope, Activity activity) {
        this(isotope, activity, Optional.empty(), List.of());
    }

    /** A source with a collimator fitted and no barrier. */
    public RadiationField(Isotope isotope, Activity activity, Collimator collimator) {
        this(isotope, activity, Optional.of(collimator), List.of());
    }

    /** A source with no collimator, behind barriers. */
    public RadiationField(Isotope isotope, Activity activity, List<Barrier> barriers) {
        this(isotope, activity, Optional.empty(), barriers);
    }

    /** The rate at a metre with nothing in the way. */
    public DoseRate unshieldedAtOneMetre() {
        return Output.outputAtOneMetre(isotope, activity);
    }

    /** Everything between the source and that bearing, multiplied together: the collimator, then each barrier. */
    public BigDecimal attenuation(BigDecimal bearingDegrees) {
        BigDecimal total = collimator.map(cup -> cup.factorAt(bearingDegrees)).orElse(BigDecimal.ONE);
        for (Barrier barrier : barriers) {
            total = Decimals.multiply(total, barrier.transmission(isotope));
        }
        return total;
    }

    /**
     * The rate a person standing there would read, to the nanosievert an hour below.
     *
     * @throws ValidationError at no distance from the source
     */
    public DoseRate rateAt(Length distance, BigDecimal bearingDegrees) {
        if (distance.um() <= 0) {
            throw new ValidationError("a rate needs a distance from the source");
        }
        BigDecimal metres = distance.m();
        return new DoseRate(
                Decimals.truncate(Decimals.divide(atOneMetre(bearingDegrees), Decimals.multiply(metres, metres))));
    }

    /**
     * How far back that rate is reached along the bearing, always rounded away from the source, to
     * the micrometre. A field with nothing in it reaches every rate at the source.
     *
     * @throws ValidationError for a target rate that is not above zero
     */
    public Length distanceTo(DoseRate target, BigDecimal bearingDegrees) {
        if (target.nsvPerHour() <= 0) {
            throw new ValidationError("a boundary needs a positive rate");
        }
        BigDecimal attenuated = atOneMetre(bearingDegrees);
        if (attenuated.signum() <= 0) {
            return Length.ZERO;
        }
        return outTo(UM_PER_M, Decimals.divide(attenuated, BigDecimal.valueOf(target.nsvPerHour())));
    }

    /**
     * How far back the public boundary is drawn along the bearing when the source is only out part
     * of any hour, always rounded away from the source.
     *
     * <p>The public limit is not a rate. It is two millirem in any one hour, so a source that is out
     * for six minutes in the hour may be roped at the distance where the rate is twenty millirem an
     * hour, which is the square root of ten times closer. The busiest hour of the shift is the one
     * that counts, and a source out for the whole hour gets the ordinary restricted area boundary.
     *
     * @throws ValidationError for a time out that is not positive or is longer than an hour
     */
    public Length publicBoundary(BigDecimal bearingDegrees, Duration outInAnyHour) {
        if (outInAnyHour.ms() <= 0 || outInAnyHour.ms() > Duration.MS_PER_HOUR) {
            throw new ValidationError("the time out is a part of one hour", Context.of("ms", outInAnyHour.ms()));
        }
        Length always = boundary(BoundaryKind.RESTRICTED, bearingDegrees);
        BigDecimal occupancy = outInAnyHour.ratio(new Duration(Duration.MS_PER_HOUR));
        return outTo(BigDecimal.valueOf(always.um()), occupancy);
    }

    /**
     * How far back that rate is reached, projected from a meter reading rather than the table, always
     * rounded away from the source.
     *
     * <p>A crew that has walked out and read forty millirem at ten metres knows more than the table
     * does, because the reading has whatever scatter the site produces already in it. Projecting that
     * reading is the honest way to place a boundary and it is what the procedure asks for whenever a
     * meter is to hand. A reading of nothing projects to no boundary.
     *
     * @throws ValidationError for a target rate that is not above zero, or a reading taken at no
     *     distance
     */
    public Length distanceFromReading(DoseRate reading, Length takenAt, DoseRate target) {
        if (target.nsvPerHour() <= 0) {
            throw new ValidationError("a boundary needs a positive rate");
        }
        if (takenAt.um() <= 0) {
            throw new ValidationError("a reading needs a distance");
        }
        if (reading.nsvPerHour() == 0) {
            return Length.ZERO;
        }
        return outTo(BigDecimal.valueOf(takenAt.um()), Decimals.divide(reading.nsvPerHour(), target.nsvPerHour()));
    }

    /** The rate at a metre along the bearing, in nanosieverts an hour, with everything in the way applied. */
    private BigDecimal atOneMetre(BigDecimal bearingDegrees) {
        return Decimals.multiply(BigDecimal.valueOf(unshieldedAtOneMetre().nsvPerHour()), attenuation(bearingDegrees));
    }

    /** The inverse square: a distance scaled by the root of a rate ratio, rounded up to the micrometre. */
    private static Length outTo(BigDecimal micrometres, BigDecimal rateRatio) {
        return new Length(Decimals.ceiling(Decimals.multiply(micrometres, Decimals.sqrt(rateRatio))));
    }

    /** How far back a posting begins along the bearing. */
    public Length boundary(BoundaryKind kind, BigDecimal bearingDegrees) {
        return distanceTo(kind.rate(), bearingDegrees);
    }

    /** Every posting along the bearing, softest first. */
    public Map<BoundaryKind, Length> boundaries(BigDecimal bearingDegrees) {
        Map<BoundaryKind, Length> out = new EnumMap<>(BoundaryKind.class);
        for (BoundaryKind kind : BoundaryKind.values()) {
            out.put(kind, boundary(kind, bearingDegrees));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Whether somebody standing there is inside the posting: the rate is above the posting's own.
     *
     * @throws ValidationError at no distance from the source
     */
    public boolean isInside(BoundaryKind kind, Length distance, BigDecimal bearingDegrees) {
        return rateAt(distance, bearingDegrees).compareTo(kind.rate()) > 0;
    }
}
