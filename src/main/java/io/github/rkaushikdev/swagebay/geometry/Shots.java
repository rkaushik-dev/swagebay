package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a shot penetrates, how many exposures a joint needs, and which shots a joint allows.
 *
 * <p>The office's order of preference is cheapest and cleanest first: panoramic, single wall,
 * elliptical, double wall single image, superimposed, and contact on plate.
 */
public final class Shots {

    private static final List<ShotType> PREFERENCE = List.of(
            ShotType.PANORAMIC,
            ShotType.SWSI,
            ShotType.DWDI_ELLIPTICAL,
            ShotType.DWSI,
            ShotType.DWDI_SUPERIMPOSED,
            ShotType.CONTACT);

    private static final BigDecimal FULL_CIRCLE = BigDecimal.valueOf(360);

    private Shots() {}

    /**
     * How much steel the beam actually crosses, cap included.
     *
     * <p>The cap counts once per wall the beam goes through, because a beam that crosses two walls
     * crosses two caps. A double wall shot on a capped weld is therefore noticeably slower than the
     * wall thickness alone suggests, and getting this wrong is the classic reason a first shot comes
     * out thin.
     */
    public static Length penetratedThickness(Joint joint, ShotType shot) {
        int walls = shot.wallsPenetrated();
        return joint.wall().times(walls).plus(joint.reinforcement().times(walls));
    }

    /** The code minimum, before anything about coverage geometry is applied; one for a straight weld. */
    public static int exposuresRequired(Joint joint, ShotType shot) {
        if (!joint.form().isCircumferential()) {
            return 1;
        }
        return shot.minimumExposures();
    }

    /** Every shot type, in declaration order, with a plain reason against the ones that are out. */
    public static List<ShotFeasibility> feasibleShots(Joint joint, Length sourceDiameter, Length guideClearance) {
        boolean inside = joint.admitsInternalSource(sourceDiameter, guideClearance);
        List<ShotFeasibility> results = new ArrayList<>();
        for (ShotType shot : ShotType.values()) {
            Optional<String> reason = reasonAgainst(joint, shot, inside);
            results.add(new ShotFeasibility(shot, reason.isEmpty(), reason));
        }
        return List.copyOf(results);
    }

    /**
     * The shot the office would pick, cheapest and cleanest first.
     *
     * @throws TechniqueError when no shot type is available on the joint
     */
    public static ShotType chooseShot(Joint joint, Length sourceDiameter, Length guideClearance) {
        Set<ShotType> available = EnumSet.noneOf(ShotType.class);
        for (ShotFeasibility feasibility : feasibleShots(joint, sourceDiameter, guideClearance)) {
            if (feasibility.available()) {
                available.add(feasibility.shot());
            }
        }
        return PREFERENCE.stream()
                .filter(available::contains)
                .findFirst()
                .orElseThrow(() -> new TechniqueError(
                        "no shot type is available on this joint", Context.of("joint", joint.describe())));
    }

    /**
     * The arc of weld each exposure has to carry, in degrees; nothing for a straight weld.
     *
     * @throws TechniqueError for fewer than one exposure
     */
    public static BigDecimal coverageArc(Joint joint, int exposures) {
        if (exposures <= 0) {
            throw new TechniqueError("a joint needs at least one exposure", Context.of("exposures", exposures));
        }
        if (!joint.form().isCircumferential()) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(FULL_CIRCLE, exposures);
    }

    private static Optional<String> reasonAgainst(Joint joint, ShotType shot, boolean inside) {
        if (shot.needsInternalAccess() && !inside) {
            return Optional.of("no room for the source inside the bore");
        }
        if (shot.isDoubleImage()) {
            if (joint.form() == JointForm.PLATE_BUTT) {
                return Optional.of("plate has no second wall");
            }
            if (!joint.isSmallBore()) {
                return Optional.of("double wall double image is limited to 89 mm outside diameter");
            }
        }
        if (shot == ShotType.DWSI && joint.form() == JointForm.PLATE_BUTT) {
            return Optional.of("plate has no second wall");
        }
        if (shot == ShotType.PANORAMIC && !joint.form().isCircumferential()) {
            return Optional.of("a panoramic shot needs a circumferential weld");
        }
        return Optional.empty();
    }
}
