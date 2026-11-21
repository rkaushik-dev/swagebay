package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;

/**
 * Which indicator goes on the shot, and what has to be visible on it.
 *
 * <p>This is the single most consulted table in the trade. Take the nominal single wall
 * thickness, find the band it falls in, read off the plaque number and the essential hole. There
 * are two columns, because a plaque on the source side casts a bigger, softer shadow than the same
 * plaque on the film side, so the film side column is one row easier all the way down.
 *
 * <p>The bands are half open at the bottom and closed at the top: over a quarter inch through
 * three eighths. A weld exactly on a boundary takes the lower band, which is the stricter one, and
 * getting that backwards is the classic way to ship a radiograph with the wrong penetrameter on it.
 *
 * <p>The ISO wire tables sit beside the hole table, because a caller never wants one family in
 * the abstract: it wants whichever family the job specification names, and {@link #selectFor}
 * reads that one. The hole table is keyed on the wall, the wire tables on penetrated thickness, and
 * mixing them up produces an indicator one or two rows too easy.
 */
public final class Selection {

    /** Every band in the hole table calls for the 2T hole. The column exists anyway. */
    public static final HoleSize ESSENTIAL_HOLE = HoleSize.TWO_T;

    /**
     * One band of the hole table.
     *
     * @param ceiling the top of the band, included
     * @param sourceSide the plaque identity for the source side
     * @param filmSide the plaque identity for the film side
     */
    public record HoleBand(Length ceiling, int sourceSide, int filmSide) {}

    /**
     * One band of a wire table.
     *
     * @param ceiling the top of the band in penetrated thickness, included
     * @param wire the essential wire number
     */
    public record WireBand(Length ceiling, int wire) {}

    private static final String HOLE_TABLE_END = "20.00";

    private static final List<HoleBand> HOLE_TABLE = List.of(
            holeBand("0.25", 12, 10),
            holeBand("0.375", 15, 12),
            holeBand("0.50", 17, 15),
            holeBand("0.75", 20, 17),
            holeBand("1.00", 25, 20),
            holeBand("1.50", 30, 25),
            holeBand("2.00", 35, 30),
            holeBand("2.50", 40, 35),
            holeBand("4.00", 50, 40),
            holeBand("6.00", 60, 50),
            holeBand("8.00", 80, 60),
            holeBand("10.00", 100, 80),
            holeBand("12.00", 120, 100),
            holeBand("16.00", 160, 120),
            holeBand(HOLE_TABLE_END, 200, 160));

    private static final List<WireBand> CLASS_A = List.of(
            wireBand("1.2", 18),
            wireBand("2.0", 17),
            wireBand("3.5", 16),
            wireBand("5.0", 15),
            wireBand("7.0", 14),
            wireBand("10", 13),
            wireBand("15", 12),
            wireBand("25", 11),
            wireBand("32", 10),
            wireBand("40", 9),
            wireBand("55", 8),
            wireBand("85", 7),
            wireBand("150", 6),
            wireBand("200", 5),
            wireBand("250", 4));

    private static final List<WireBand> CLASS_B = List.of(
            wireBand("1.5", 19),
            wireBand("2.5", 18),
            wireBand("4.0", 17),
            wireBand("6.0", 16),
            wireBand("8.0", 15),
            wireBand("12", 14),
            wireBand("20", 13),
            wireBand("30", 12),
            wireBand("35", 11),
            wireBand("45", 10),
            wireBand("65", 9),
            wireBand("120", 8),
            wireBand("200", 7),
            wireBand("350", 6));

    private Selection() {}

    /**
     * Read the hole table. Bands are closed at the top, so a boundary reads down.
     *
     * @throws ValidationError for a thickness that is not positive
     * @throws TechniqueError past the end of the table at twenty inches
     */
    public static HoleSelection holeSelection(Length thickness, IndicatorSide side) {
        if (thickness.um() <= 0) {
            throw new ValidationError("a nominal thickness has to be positive");
        }
        for (HoleBand band : HOLE_TABLE) {
            if (thickness.compareTo(band.ceiling()) <= 0) {
                int identity = side == IndicatorSide.SOURCE ? band.sourceSide() : band.filmSide();
                return new HoleSelection(thickness, side, new HoleIndicator(identity), ESSENTIAL_HOLE);
            }
        }
        throw new TechniqueError(
                "that thickness is past the end of the indicator table",
                Context.of("thickness_mm", thickness.mm().toString(), "table_ends_at_in", HOLE_TABLE_END));
    }

    /**
     * The top of the band a thickness falls in, for showing the working.
     *
     * @throws TechniqueError past the end of the table
     */
    public static Length bandCeiling(Length thickness) {
        for (HoleBand band : HOLE_TABLE) {
            if (thickness.compareTo(band.ceiling()) <= 0) {
                return band.ceiling();
            }
        }
        throw new TechniqueError("that thickness is past the end of the indicator table");
    }

    /** Whether a thickness sits exactly on a band edge, which reads down. */
    public static boolean isBoundaryThickness(Length thickness) {
        return HOLE_TABLE.stream().anyMatch(band -> band.ceiling().equals(thickness));
    }

    /** The whole hole table, thinnest band first. */
    public static List<HoleBand> tableBands() {
        return HOLE_TABLE;
    }

    /**
     * Read the wire table for a technique class on penetrated thickness.
     *
     * @throws ValidationError for a thickness that is not positive
     * @throws TechniqueError past the end of the class table
     */
    public static WireSelection wireSelection(Length penetrated, TechniqueClass techniqueClass) {
        if (penetrated.um() <= 0) {
            throw new ValidationError("a penetrated thickness has to be positive");
        }
        for (WireBand band : table(techniqueClass)) {
            if (penetrated.compareTo(band.ceiling()) <= 0) {
                return new WireSelection(
                        penetrated,
                        techniqueClass,
                        new WireIndicator(band.wire()),
                        Indicators.wireSetsContaining(band.wire()));
            }
        }
        throw new TechniqueError(
                "that penetrated thickness is past the end of the wire table",
                Context.of("penetrated_mm", penetrated.mm().toString(), "technique_class", techniqueClass.value()));
    }

    /** Whether the improved class actually asks for a finer wire at this penetrated thickness. */
    public static boolean classBIsStricter(Length penetrated) {
        WireSelection basic = wireSelection(penetrated, TechniqueClass.A);
        WireSelection improved = wireSelection(penetrated, TechniqueClass.B);
        return improved.wire().diameter().compareTo(basic.wire().diameter()) < 0;
    }

    /** The thickest penetration a class table covers. */
    public static Length tableCeiling(TechniqueClass techniqueClass) {
        List<WireBand> table = table(techniqueClass);
        return table.get(table.size() - 1).ceiling();
    }

    /** The wire table of a class, thinnest band first. */
    public static List<WireBand> bands(TechniqueClass techniqueClass) {
        return table(techniqueClass);
    }

    /**
     * Read whichever table the family names, from the source side in class A.
     *
     * @see #selectFor(Family, Length, Length, IndicatorSide, TechniqueClass)
     */
    public static IndicatorSelection selectFor(Family family, Length wall, Length penetrated) {
        return selectFor(family, wall, penetrated, IndicatorSide.SOURCE, TechniqueClass.A);
    }

    /**
     * One entry point, reading whichever table the family names.
     *
     * <p>Both thicknesses are taken rather than one, so the caller cannot hand the wrong one in. The
     * hole table reads the wall on the given side; a wire table reads the penetrated thickness in the
     * given class.
     */
    public static IndicatorSelection selectFor(
            Family family, Length wall, Length penetrated, IndicatorSide side, TechniqueClass techniqueClass) {
        if (family == Family.HOLE) {
            return holeSelection(wall, side);
        }
        return wireSelection(penetrated, techniqueClass);
    }

    private static List<WireBand> table(TechniqueClass techniqueClass) {
        return techniqueClass.isImproved() ? CLASS_B : CLASS_A;
    }

    private static HoleBand holeBand(String ceilingInches, int sourceSide, int filmSide) {
        return new HoleBand(Length.fromInch(ceilingInches), sourceSide, filmSide);
    }

    private static WireBand wireBand(String ceilingMm, int wire) {
        return new WireBand(Length.fromMm(ceilingMm), wire);
    }
}
