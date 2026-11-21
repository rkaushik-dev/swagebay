package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.materials.Material;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nominal pipe size and wall schedule, from ASME B36.10M.
 *
 * <p>A work order says six inch schedule forty. It does not say 168.3 mm outside diameter and 7.11
 * mm wall, and the shooter is not going to look it up on a phone at four in the morning in a lay
 * down yard. This is the table.
 *
 * <p>Two things about nominal size trip people up. Nominal is not a measurement: a two inch line
 * is 60.3 mm across, which is not two inches of anything. And outside diameter is fixed for a
 * given nominal size no matter what the schedule is, which is the whole point of the schedule
 * system, so a heavier wall eats the bore rather than growing the pipe.
 *
 * <p>Only the schedules the shop actually meets are in here. A line list that asks for something
 * else gets a refusal and somebody enters the wall by hand, which is better than a wall that was
 * interpolated out of nowhere.
 */
public final class PipeSchedule {

    /** The schedules the table carries. */
    public static final List<String> SCHEDULES = List.of("STD", "XS", "XXS", "40", "80", "160");

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.03");

    /** Nominal size to its outside diameter in millimetres and its wall in millimetres by schedule. */
    private static final Map<String, Row> TABLE = new LinkedHashMap<>();

    private record Row(String outsideDiameterMm, Map<String, String> wallsMm) {}

    static {
        publish("1/2", "21.3", "2.77", "3.73", "7.47", "2.77", "3.73", "4.78");
        publish("3/4", "26.7", "2.87", "3.91", "7.82", "2.87", "3.91", "5.56");
        publish("1", "33.4", "3.38", "4.55", "9.09", "3.38", "4.55", "6.35");
        publish("1-1/4", "42.2", "3.56", "4.85", "9.70", "3.56", "4.85", "6.35");
        publish("1-1/2", "48.3", "3.68", "5.08", "10.15", "3.68", "5.08", "7.14");
        publish("2", "60.3", "3.91", "5.54", "11.07", "3.91", "5.54", "8.74");
        publish("2-1/2", "73.0", "5.16", "7.01", "14.02", "5.16", "7.01", "9.53");
        publish("3", "88.9", "5.49", "7.62", "15.24", "5.49", "7.62", "11.13");
        publish("4", "114.3", "6.02", "8.56", "17.12", "6.02", "8.56", "13.49");
        publish("5", "141.3", "6.55", "9.53", "19.05", "6.55", "9.53", "15.88");
        publish("6", "168.3", "7.11", "10.97", "21.95", "7.11", "10.97", "18.26");
        publish("8", "219.1", "8.18", "12.70", "22.23", "8.18", "12.70", "23.01");
        publish("10", "273.0", "9.27", "12.70", "25.40", "9.27", "15.09", "28.58");
        publish("12", "323.8", "9.53", "12.70", "25.40", "10.31", "17.48", "33.32");
        publishWithoutDoubleExtraStrong("14", "355.6", "9.53", "12.70", "11.13", "19.05", "35.71");
        publishWithoutDoubleExtraStrong("16", "406.4", "9.53", "12.70", "12.70", "21.44", "40.49");
        publishWithoutDoubleExtraStrong("18", "457.2", "9.53", "12.70", "14.27", "23.83", "45.24");
        publishWithoutDoubleExtraStrong("20", "508.0", "9.53", "12.70", "15.09", "26.19", "50.01");
        publishWithoutDoubleExtraStrong("24", "609.6", "9.53", "12.70", "17.48", "30.96", "59.54");
    }

    private PipeSchedule() {}

    /** Every nominal size in the table, smallest first. */
    public static List<String> nominalSizes() {
        return List.copyOf(TABLE.keySet());
    }

    /**
     * The published outside diameter of a nominal size, whatever the schedule.
     *
     * @throws ConfigurationError for a size the table does not carry
     */
    public static Length outsideDiameter(String nominal) {
        return Length.fromMm(row(nominal).outsideDiameterMm());
    }

    /**
     * The published wall at that size and schedule; the schedule is read in any case.
     *
     * @throws ConfigurationError for a size the table does not carry, or a schedule not published at
     *     that size
     */
    public static Length wall(String nominal, String schedule) {
        String wall = row(nominal).wallsMm().get(schedule.toUpperCase(Locale.ROOT));
        if (wall == null) {
            throw new ConfigurationError(
                    "that schedule is not published at this size",
                    Context.of("nominal", nominal, "schedule", schedule));
        }
        return Length.fromMm(wall);
    }

    /** The size and schedule resolved to their diameter and wall. */
    public static PipeSize resolve(String nominal, String schedule) {
        return new PipeSize(
                nominal, outsideDiameter(nominal), schedule.toUpperCase(Locale.ROOT), wall(nominal, schedule));
    }

    /** The joint a line list line turns into, ground flush. */
    public static Joint girthWeld(String nominal, String schedule, Material material) {
        return girthWeld(nominal, schedule, material, Length.ZERO);
    }

    /** The joint a line list line turns into, with the cap the welder left. */
    public static Joint girthWeld(String nominal, String schedule, Material material, Length reinforcement) {
        PipeSize size = resolve(nominal, schedule);
        return Joint.builder(JointForm.PIPE_GIRTH, size.wall(), material)
                .outsideDiameter(size.outsideDiameter())
                .reinforcement(reinforcement)
                .build();
    }

    /** Work back from a tape measure to a nominal size, within three percent. */
    public static String nearestNominal(Length measured) {
        return nearestNominal(measured, DEFAULT_TOLERANCE);
    }

    /**
     * Work back from a tape measure to a nominal size, within a tolerance given as a fraction of the
     * published diameter. The first size in the table wins a tie.
     *
     * @throws ConfigurationError when no size is within the tolerance
     */
    public static String nearestNominal(Length measured, BigDecimal tolerance) {
        BigDecimal bestError = null;
        String bestNominal = null;
        for (Map.Entry<String, Row> entry : TABLE.entrySet()) {
            Length published = Length.fromMm(entry.getValue().outsideDiameterMm());
            BigDecimal error = Decimals.divide(
                            BigDecimal.valueOf(measured.um() - published.um()), BigDecimal.valueOf(published.um()))
                    .abs();
            if (bestError == null || error.compareTo(bestError) < 0) {
                bestError = error;
                bestNominal = entry.getKey();
            }
        }
        if (bestError == null || bestError.compareTo(tolerance) > 0) {
            throw new ConfigurationError(
                    "no nominal size is close enough to that measurement",
                    Context.of("mm", measured.mm().toString()));
        }
        return bestNominal;
    }

    private static Row row(String nominal) {
        Row row = TABLE.get(nominal);
        if (row == null) {
            throw new ConfigurationError("no such nominal pipe size", Context.of("nominal", nominal));
        }
        return row;
    }

    private static void publish(
            String nominal, String od, String std, String xs, String xxs, String s40, String s80, String s160) {
        Map<String, String> walls = new LinkedHashMap<>();
        walls.put("STD", std);
        walls.put("XS", xs);
        walls.put("XXS", xxs);
        walls.put("40", s40);
        walls.put("80", s80);
        walls.put("160", s160);
        TABLE.put(nominal, new Row(od, Collections.unmodifiableMap(walls)));
    }

    /** The sizes from fourteen inch up, where double extra strong is not published. */
    private static void publishWithoutDoubleExtraStrong(
            String nominal, String od, String std, String xs, String s40, String s80, String s160) {
        Map<String, String> walls = new LinkedHashMap<>();
        walls.put("STD", std);
        walls.put("XS", xs);
        walls.put("40", s40);
        walls.put("80", s80);
        walls.put("160", s160);
        TABLE.put(nominal, new Row(od, Collections.unmodifiableMap(walls)));
    }
}
