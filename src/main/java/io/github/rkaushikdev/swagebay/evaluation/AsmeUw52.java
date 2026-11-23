package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ASME Section VIII UW-52, the acceptance standard for a vessel examined by spot radiography.
 *
 * <p>It reads like a relaxed UW-51 and it is not one. Cracks and every zone of incomplete fusion
 * or penetration are unacceptable at any length under c1, with no sliding allowance the way UW-51
 * has. Slag and cavities are allowed two thirds of the wall under c2, capped at three quarters of
 * an inch, and anything under a quarter inch passes whatever the wall. Rounded indications are not
 * a factor at all, which is what the vessel bought by taking the lower joint efficiency.
 *
 * <p>The aligned rule is the one that gets misapplied. Slag in line is acceptable while the total
 * stays at one wall in six walls of weld, and a weld shorter than six walls gets that allowance in
 * proportion. Inclusions only count as in line while the weld between them is shorter than three
 * times the longest; three lengths of sound weld or more and they are judged apart.
 */
public final class AsmeUw52 {

    /** The longest slag or cavity allowed as a fraction of the wall. */
    public static final BigDecimal SLAG_FRACTION_OF_WALL = new BigDecimal("0.6666666666666666666666666667");

    /** The longest slag or cavity allowed at any wall. */
    public static final Length SLAG_CAP = Length.fromInch("0.75");

    /** Slag or a cavity shorter than this is acceptable at any wall. */
    public static final Length ALWAYS_ACCEPTABLE = Length.fromInch("0.25");

    /** The aligned window, in wall thicknesses. */
    public static final int WINDOW_IN_WALLS = 6;

    /** Sound weld of this many times the longest inclusion keeps two inclusions apart. */
    public static final int SEPARATION_MULTIPLE = 3;

    private static final Set<Kind> FUSION_AND_PENETRATION = EnumSet.of(Kind.IP, Kind.IPD, Kind.ICP, Kind.IF, Kind.IFD);
    private static final Set<Kind> SLAG_AND_CAVITIES = EnumSet.of(Kind.ESI, Kind.ISI, Kind.HB);

    private AsmeUw52() {}

    /**
     * The longest slag inclusion or cavity UW-52 allows at that wall.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length slagLimit(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        Length scaled = wall.times(SLAG_FRACTION_OF_WALL);
        return scaled.compareTo(SLAG_CAP) <= 0 ? scaled : SLAG_CAP;
    }

    /**
     * What slag in line may add up to on that weld: the wall, or a share of it on a weld shorter
     * than six walls.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length alignedAllowance(Length wall, Length weldLength) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        Length window = wall.times(WINDOW_IN_WALLS);
        if (weldLength.compareTo(window) >= 0) {
            return wall;
        }
        return wall.times(weldLength.ratio(window));
    }

    /** Whether UW-52 has anything to say about that kind; rounded ones it ignores. */
    public static boolean judges(Kind kind) {
        return kind.isAlwaysRejectable() || FUSION_AND_PENETRATION.contains(kind) || SLAG_AND_CAVITIES.contains(kind);
    }

    /**
     * Every UW-52 finding on the weld at that wall, c1 first and then c2.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static List<Finding> evaluate(IndicationSet found, Length wall) {
        Length limit = slagLimit(wall);
        List<Finding> out = new ArrayList<>();
        for (Indication indication : found.indications()) {
            Kind kind = indication.kind();
            if (kind.isAlwaysRejectable() || FUSION_AND_PENETRATION.contains(kind)) {
                out.add(new Finding(
                        "UW-52(c1)",
                        kind,
                        kind.value() + " is not acceptable at any length on a spot examined weld",
                        List.of(indication)));
            }
        }
        List<Indication> slag = found.indications().stream()
                .filter(indication -> SLAG_AND_CAVITIES.contains(indication.kind()))
                .toList();
        for (Indication indication : slag) {
            if (indication.length().compareTo(ALWAYS_ACCEPTABLE) >= 0
                    && indication.length().compareTo(limit) > 0) {
                out.add(new Finding(
                        "UW-52(c2)",
                        indication.kind(),
                        indication.length().formatMm(1) + " is over the " + limit.formatMm(1) + " allowed at "
                                + wall.formatMm(1) + " wall",
                        List.of(indication)));
            }
        }
        Length allowance = alignedAllowance(wall, found.weldLength());
        Length window = wall.times(WINDOW_IN_WALLS);
        for (AsmeUw51.Group group : AsmeUw51.groupAligned(slag, SEPARATION_MULTIPLE, true)) {
            if (group.size() < 2) {
                continue;
            }
            Window worst = Aggregates.worstWindow(group.members(), found.weldLength(), window);
            if (worst.aggregate().compareTo(allowance) > 0) {
                out.add(new Finding(
                        "UW-52(c2)",
                        group.members().get(0).kind(),
                        group.size() + " inclusions in line total "
                                + worst.aggregate().formatMm(1) + " in "
                                + worst.length().formatMm(1) + ", over the " + allowance.formatMm(1) + " allowed",
                        group.members()));
            }
        }
        return List.copyOf(out);
    }
}
