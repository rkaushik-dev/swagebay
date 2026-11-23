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
 * Nominal pipe size and wall schedule, from ASME B36.10M and B36.19M.
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
 * <p>The stainless schedules with an S after them are ASME B36.19M and they are not the same walls
 * as the carbon steel numbers without one: ten S on a two inch line is 2.77 mm, and schedule ten is
 * not published there at all. A line list that asks for something the table does not carry gets a
 * refusal and somebody enters the wall by hand, which is better than a wall that was interpolated
 * out of nowhere.
 */
public final class PipeSchedule {

    /** The schedules the table carries, in the order the columns are printed. */
    public static final List<String> SCHEDULES = List.of(
            "5S", "10S", "10", "20", "30", "40S", "STD", "40", "60", "80S", "XS", "80", "100", "120", "140", "160",
            "XXS");

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.03");

    private static final String NOT_PUBLISHED = "-";

    /**
     * The table as the standards print it: nominal size, outside diameter, then a wall in
     * millimetres for every schedule in {@link #SCHEDULES}, a dash where none is published.
     */
    private static final List<String> GRID = List.of(
            "1/2    21.3  1.65 2.11 -    -    -     2.77 2.77 2.77  -     3.73  3.73  3.73  -     -     -     4.78  7.47",
            "3/4    26.7  1.65 2.11 -    -    -     2.87 2.87 2.87  -     3.91  3.91  3.91  -     -     -     5.56  7.82",
            "1      33.4  1.65 2.77 -    -    -     3.38 3.38 3.38  -     4.55  4.55  4.55  -     -     -     6.35  9.09",
            "1-1/4  42.2  1.65 2.77 -    -    -     3.56 3.56 3.56  -     4.85  4.85  4.85  -     -     -     6.35  9.70",
            "1-1/2  48.3  1.65 2.77 -    -    -     3.68 3.68 3.68  -     5.08  5.08  5.08  -     -     -     7.14  10.15",
            "2      60.3  1.65 2.77 -    -    -     3.91 3.91 3.91  -     5.54  5.54  5.54  -     -     -     8.74  11.07",
            "2-1/2  73.0  2.11 3.05 -    -    -     5.16 5.16 5.16  -     7.01  7.01  7.01  -     -     -     9.53  14.02",
            "3      88.9  2.11 3.05 -    -    -     5.49 5.49 5.49  -     7.62  7.62  7.62  -     -     -     11.13 15.24",
            "4      114.3 2.11 3.05 -    -    -     6.02 6.02 6.02  -     8.56  8.56  8.56  -     11.13 -     13.49 17.12",
            "5      141.3 2.77 3.40 -    -    -     6.55 6.55 6.55  -     9.53  9.53  9.53  -     12.70 -     15.88 19.05",
            "6      168.3 2.77 3.40 -    -    -     7.11 7.11 7.11  -     10.97 10.97 10.97 -     14.27 -     18.26 21.95",
            "8      219.1 2.77 3.76 -    6.35 7.04  8.18 8.18 8.18  10.31 12.70 12.70 12.70 15.09 18.26 20.62 23.01 22.23",
            "10     273.0 3.40 4.19 -    6.35 7.80  9.27 9.27 9.27  12.70 12.70 12.70 15.09 18.26 21.44 25.40 28.58 25.40",
            "12     323.8 3.96 4.57 -    6.35 8.38  9.53 9.53 10.31 14.27 12.70 12.70 17.48 21.44 25.40 28.58 33.32 25.40",
            "14     355.6 3.96 4.78 6.35 7.92 9.53  -    9.53 11.13 15.09 -     12.70 19.05 23.83 27.79 31.75 35.71 -",
            "16     406.4 4.19 4.78 6.35 7.92 9.53  -    9.53 12.70 16.66 -     12.70 21.44 26.19 30.96 36.53 40.49 -",
            "18     457.2 4.19 4.78 6.35 7.92 11.13 -    9.53 14.27 19.05 -     12.70 23.83 29.36 34.93 39.67 45.24 -",
            "20     508.0 4.78 5.54 6.35 9.53 12.70 -    9.53 15.09 20.62 -     12.70 26.19 32.54 38.10 44.45 50.01 -",
            "24     609.6 5.54 6.35 6.35 9.53 14.27 -    9.53 17.48 24.61 -     12.70 30.96 38.89 46.02 52.37 59.54 -");

    /** Nominal size to its outside diameter and its wall by schedule. */
    private static final Map<String, Row> TABLE = parse(GRID);

    private record Row(Length outsideDiameter, Map<String, Length> walls) {}

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
        return row(nominal).outsideDiameter();
    }

    /**
     * The published wall at that size and schedule; the schedule is read in any case.
     *
     * @throws ConfigurationError for a size the table does not carry, or a schedule not published at
     *     that size
     */
    public static Length wall(String nominal, String schedule) {
        Length wall = row(nominal).walls().get(schedule.toUpperCase(Locale.ROOT));
        if (wall == null) {
            throw new ConfigurationError(
                    "that schedule is not published at this size",
                    Context.of("nominal", nominal, "schedule", schedule));
        }
        return wall;
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
            Length published = entry.getValue().outsideDiameter();
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

    /** Read the printed grid into rows, refusing a line with the wrong number of columns. */
    private static Map<String, Row> parse(List<String> grid) {
        Map<String, Row> table = new LinkedHashMap<>();
        for (String line : grid) {
            String[] cells = line.trim().split("\\s+");
            if (cells.length != SCHEDULES.size() + 2) {
                throw new ConfigurationError(
                        "a pipe schedule row has the wrong number of columns", Context.of("row", cells[0]));
            }
            Map<String, Length> walls = new LinkedHashMap<>();
            for (int i = 0; i < SCHEDULES.size(); i++) {
                String cell = cells[i + 2];
                if (!cell.equals(NOT_PUBLISHED)) {
                    walls.put(SCHEDULES.get(i), Length.fromMm(cell));
                }
            }
            table.put(cells[0], new Row(Length.fromMm(cells[1]), Collections.unmodifiableMap(walls)));
        }
        return Collections.unmodifiableMap(table);
    }
}
