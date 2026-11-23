package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;

/**
 * The API 1104 acceptance criteria, as a table rather than as a pile of ifs.
 *
 * <p>Section nine is the reason this package exists in the shape it does. Every imperfection has its
 * own rule, the rules are not the same shape as each other, and several of them depend on the wall
 * thickness rather than being fixed. Each row is a {@link Criterion}.
 *
 * <p>Nothing here evaluates anything. This is the transcription and it is kept separate on purpose,
 * so that a revision of the standard is a change to the table and to nothing else; {@link
 * Assessment#assess} applies it.
 */
public final class Api1104 {

    /** The twelve inch window every aggregate is written on. */
    public static final Length WINDOW = Length.fromInch(12);

    /** The aggregate a short weld's accumulation is judged against. */
    public static final Length ACCUMULATION_AGGREGATE = Length.fromInch(2);

    /** Eight percent of a weld shorter than the window. */
    public static final BigDecimal ACCUMULATION_FRACTION = new BigDecimal("0.08");

    /** The deepest undercut accepted on heavy wall. */
    public static final Length UNDERCUT_MAX_DEPTH = Length.fromMm("0.8");

    /** The deepest undercut accepted on thin wall, as a fraction of the wall. */
    public static final BigDecimal UNDERCUT_DEPTH_FRACTION = new BigDecimal("0.125");

    /** Undercut this shallow is acceptable at any length. */
    public static final Length UNDERCUT_ALWAYS_ACCEPTABLE = Length.fromMm("0.4");

    /** A sixth of a weld shorter than the window, which is what undercut is totalled against there. */
    public static final BigDecimal UNDERCUT_SHORT_WELD_FRACTION = Decimals.divide(1, 6);

    private static final List<Criterion> CRITERIA = List.of(
            Criterion.builder(Kind.IP, "9.3.1")
                    .maxLength(Length.fromInch(1))
                    .maxAggregate(Length.fromInch(1))
                    .shortWeldFraction(ACCUMULATION_FRACTION)
                    .build(),
            Criterion.builder(Kind.IPD, "9.3.2")
                    .maxLength(Length.fromInch(2))
                    .maxAggregate(Length.fromInch(3))
                    .build(),
            Criterion.builder(Kind.ICP, "9.3.3")
                    .maxLength(Length.fromInch(2))
                    .maxAggregate(Length.fromInch(2))
                    .build(),
            Criterion.builder(Kind.IF, "9.3.4")
                    .maxLength(Length.fromInch(1))
                    .maxAggregate(Length.fromInch(1))
                    .shortWeldFraction(ACCUMULATION_FRACTION)
                    .build(),
            Criterion.builder(Kind.IFD, "9.3.5")
                    .maxLength(Length.fromInch(2))
                    .maxAggregate(Length.fromInch(2))
                    .shortWeldFraction(ACCUMULATION_FRACTION)
                    .build(),
            Criterion.builder(Kind.IC, "9.3.6")
                    .note("acceptable at any length while the density stays under the parent metal")
                    .build(),
            Criterion.builder(Kind.BT, "9.3.7")
                    .maxLength(Length.fromInch("0.5"))
                    .maxLengthFractionOfWall(new BigDecimal("0.5"))
                    .maxAggregate(Length.fromInch("0.5"))
                    .build(),
            Criterion.builder(Kind.ESI, "9.3.8")
                    .maxLength(Length.fromInch(2))
                    .maxWidth(Length.fromMm("1.6"))
                    .maxAggregate(Length.fromInch(2))
                    .build(),
            Criterion.builder(Kind.ISI, "9.3.8")
                    .maxWidth(Length.fromMm(3))
                    .maxAggregate(Length.fromInch("0.5"))
                    .maxCount(4)
                    .build(),
            Criterion.builder(Kind.P, "9.3.9")
                    .maxWidth(Length.fromMm(3))
                    .maxLengthFractionOfWall(new BigDecimal("0.25"))
                    .build(),
            Criterion.builder(Kind.CP, "9.3.9")
                    .maxLength(Length.fromInch("0.5"))
                    .maxAggregate(Length.fromInch("0.5"))
                    .build(),
            Criterion.builder(Kind.HB, "9.3.9")
                    .maxLength(Length.fromInch("0.5"))
                    .maxAggregate(Length.fromInch(2))
                    .build(),
            Criterion.builder(Kind.C, "9.3.10")
                    .maxLength(Length.fromMm(4))
                    .note("only a shallow crater or star crack under four millimetres is acceptable")
                    .build(),
            Criterion.builder(Kind.U, "9.3.11")
                    .maxLength(Length.fromInch(2))
                    .maxAggregate(Length.fromInch(2))
                    .shortWeldFraction(UNDERCUT_SHORT_WELD_FRACTION)
                    .note("depth is capped at 0.8 mm or an eighth of the wall, whichever is smaller,"
                            + " and undercut no deeper than 0.4 mm is acceptable at any length")
                    .build(),
            Criterion.builder(Kind.AB, "9.3.12")
                    .note("an arc burn is removed and the area is examined again")
                    .build());

    private Api1104() {}

    /**
     * The row for a kind.
     *
     * @throws ConfigurationError for a kind the table has no row for, rather than a default
     */
    public static Criterion criterion(Kind kind) {
        return criterion(kind, CRITERIA);
    }

    /** The row for a kind in that table, refused rather than defaulted when there is none. */
    static Criterion criterion(Kind kind, List<Criterion> table) {
        for (Criterion row : table) {
            if (row.kind() == kind) {
                return row;
            }
        }
        throw new ConfigurationError("no api 1104 criterion for that kind", Context.of("kind", kind.code()));
    }

    /** Every row, in the order of the standard. */
    public static List<Criterion> allCriteria() {
        return CRITERIA;
    }

    /**
     * Whether an indication is undercut measured no deeper than {@link #UNDERCUT_ALWAYS_ACCEPTABLE}.
     *
     * <p>Only a recorded depth can show that. Undercut with no depth on it is judged on length like
     * any other, because the reader who did not measure it cannot say it was shallow.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static boolean isShallowUndercut(Indication indication, Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        return indication.kind() == Kind.U
                && indication
                        .depthFraction()
                        .map(fraction -> wall.times(fraction).compareTo(UNDERCUT_ALWAYS_ACCEPTABLE) <= 0)
                        .orElse(false);
    }

    /**
     * Whichever of the two published undercut depths is smaller at this wall.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length undercutDepthLimit(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        Length scaled = wall.times(UNDERCUT_DEPTH_FRACTION);
        return UNDERCUT_MAX_DEPTH.compareTo(scaled) <= 0 ? UNDERCUT_MAX_DEPTH : scaled;
    }
}
