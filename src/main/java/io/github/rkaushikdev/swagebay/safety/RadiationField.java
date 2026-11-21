package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
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
        BigDecimal total = BigDecimal.ONE;
        if (collimator.isPresent()) {
            total = Decimals.multiply(total, collimator.get().factorAt(bearingDegrees));
        }
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
        BigDecimal unshielded = Decimals.divide(
                BigDecimal.valueOf(unshieldedAtOneMetre().nsvPerHour()), Decimals.multiply(metres, metres));
        return new DoseRate(Decimals.truncate(Decimals.multiply(unshielded, attenuation(bearingDegrees))));
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
        BigDecimal attenuated =
                Decimals.multiply(BigDecimal.valueOf(unshieldedAtOneMetre().nsvPerHour()), attenuation(bearingDegrees));
        if (attenuated.signum() <= 0) {
            return Length.ZERO;
        }
        BigDecimal metres = Decimals.sqrt(Decimals.divide(attenuated, BigDecimal.valueOf(target.nsvPerHour())));
        return new Length(Decimals.ceiling(Decimals.multiply(metres, UM_PER_M)));
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
        BigDecimal ratio = Decimals.sqrt(Decimals.divide(reading.nsvPerHour(), target.nsvPerHour()));
        return new Length(Decimals.ceiling(Decimals.multiply(BigDecimal.valueOf(takenAt.um()), ratio)));
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
