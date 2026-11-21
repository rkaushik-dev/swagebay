package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Where the indicators go, how many, and when a shim goes under one.
 *
 * <p>Three rules, and every one of them gets broken on site.
 *
 * <p>One indicator per radiograph. Obvious until a single exposure covers the whole circumference
 * of a pipe, at which point one indicator says nothing about the part of the weld a hundred and
 * eighty degrees away, so a panoramic shot takes three, spaced round the pipe.
 *
 * <p>The indicator goes on the source side. It is further from the film there, its shadow is bigger
 * and softer, and that is the point: if the technique can show a softened indicator it can
 * certainly show a sharp flaw. Film side is allowed when the source side cannot be reached, the
 * indicator is one row easier to make up for it, and a lead F goes beside it so a reviewer knows.
 *
 * <p>An indicator lies on the parent metal, and the parent metal is thinner than the weld with its
 * cap. Reading a plaque that is sitting on ten millimetres when the weld is thirteen flatters the
 * technique. So a shim goes under the plaque to make up the difference, the shim is the same
 * material as the part, and it has to be bigger than the plaque so its own edge does not land
 * inside the area being read.
 */
public final class Placements {

    /** How many indicators a panoramic shot takes. */
    public static final int PANORAMIC_INDICATORS = 3;

    /** How far the shim has to stand proud of the plaque on every side. */
    public static final Length SHIM_MARGIN = Length.fromMm(3);

    private static final BigDecimal FULL_CIRCLE = BigDecimal.valueOf(360);

    private Placements() {}

    /** A shim is needed exactly when the cap stands proud of the parent metal; it is as thick as the cap. */
    public static Optional<Shim> shimFor(Joint joint) {
        if (joint.reinforcement().um() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Shim(joint.reinforcement(), joint.material().key()));
    }

    /**
     * The smallest shim that stands proud of the plaque on every side.
     *
     * @throws ValidationError for a plaque with no size
     */
    public static Length minimumShimSize(Length plaqueLongSide) {
        if (plaqueLongSide.um() <= 0) {
            throw new ValidationError("a plaque has a size");
        }
        return plaqueLongSide.plus(SHIM_MARGIN.times(2));
    }

    /**
     * One indicator per radiograph, except a panoramic shot, which takes three.
     *
     * @throws ValidationError for fewer than one exposure
     */
    public static int indicatorsRequired(ShotType shot, int exposures) {
        if (exposures <= 0) {
            throw new ValidationError("a joint needs at least one exposure", Context.of("exposures", exposures));
        }
        if (shot == ShotType.PANORAMIC) {
            return PANORAMIC_INDICATORS;
        }
        return exposures;
    }

    /** The three panoramic positions: twelve, four and eight. */
    public static List<ClockPosition> panoramicPositions() {
        return panoramicPositions(PANORAMIC_INDICATORS);
    }

    /**
     * Positions spaced evenly round the pipe, starting at the top.
     *
     * @throws ValidationError for fewer than one indicator
     */
    public static List<ClockPosition> panoramicPositions(int count) {
        if (count <= 0) {
            throw new ValidationError("a panoramic shot needs at least one indicator");
        }
        return evenlySpaced(count);
    }

    /** Every indicator the joint needs on the source side. */
    public static List<Placement> planPlacements(Joint joint, ShotType shot, int exposures) {
        return planPlacements(joint, shot, exposures, IndicatorSide.SOURCE);
    }

    /**
     * Every indicator the joint needs, with its position and its shim: spaced the way the exposures
     * are on a circumferential weld, and with no clock position on a straight one.
     *
     * @throws ValidationError for fewer than one exposure
     */
    public static List<Placement> planPlacements(Joint joint, ShotType shot, int exposures, IndicatorSide side) {
        int count = indicatorsRequired(shot, exposures);
        Optional<Shim> shim = shimFor(joint);
        if (shot == ShotType.PANORAMIC) {
            return panoramicPositions(count).stream()
                    .map(position -> new Placement(side, Optional.of(position), shim))
                    .toList();
        }
        if (!joint.form().isCircumferential()) {
            return Collections.nCopies(count, new Placement(side, Optional.empty(), shim));
        }
        return evenlySpaced(count).stream()
                .map(position -> new Placement(side, Optional.of(position), shim))
                .toList();
    }

    /**
     * Refuse a shim whose edge would land inside the area being read.
     *
     * @throws TechniqueError when the shim does not stand proud of the plaque
     */
    public static void checkShimCovers(Length plaqueLongSide, Length shimSide) {
        Length needed = minimumShimSize(plaqueLongSide);
        if (shimSide.compareTo(needed) < 0) {
            throw new TechniqueError(
                    "the shim does not stand proud of the plaque",
                    Context.of(
                            "shim_mm",
                            shimSide.mm().toString(),
                            "needed_mm",
                            needed.mm().toString()));
        }
    }

    private static List<ClockPosition> evenlySpaced(int count) {
        BigDecimal step = Decimals.divide(FULL_CIRCLE, count);
        List<ClockPosition> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            positions.add(ClockPosition.fromDegrees(Decimals.multiply(step, BigDecimal.valueOf(i))));
        }
        return List.copyOf(positions);
    }
}
