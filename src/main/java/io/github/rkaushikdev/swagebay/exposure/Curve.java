package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One kilovoltage of a tube chart, and the thickness to exposure points along it.
 *
 * <p>The value read off is milliampere seconds, and it is read between points along the log scale,
 * exactly as an isotope chart is.
 *
 * @param kv the kilovoltage the curve was drawn at
 * @param points the readings, thinnest first
 */
public record Curve(int kv, List<Point> points) {

    /**
     * One reading along a curve.
     *
     * @param thickness the steel thickness
     * @param mas the exposure in milliampere seconds
     */
    public record Point(Length thickness, BigDecimal mas) {}

    /**
     * A curve; the points are copied.
     *
     * @throws ConfigurationError for fewer than two points, points out of thickness order, or a curve
     *     that falls
     */
    public Curve {
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new ConfigurationError("a curve needs at least two points", Context.of("kv", kv));
        }
        List<Length> thicknesses = points.stream().map(Point::thickness).toList();
        if (!thicknesses.equals(thicknesses.stream().sorted().toList())) {
            throw new ConfigurationError("curve points are out of order", Context.of("kv", kv));
        }
        List<BigDecimal> exposures = points.stream().map(Point::mas).toList();
        if (!exposures.equals(exposures.stream().sorted().toList())) {
            throw new ConfigurationError("a curve has to rise", Context.of("kv", kv));
        }
    }

    /** A curve from the numbers as they are written on the card: thicknesses in millimetres, then mAs. */
    public static Curve fromMm(int kv, List<Map.Entry<String, String>> readings) {
        return new Curve(
                kv,
                readings.stream()
                        .map(reading -> new Point(Length.fromMm(reading.getKey()), new BigDecimal(reading.getValue())))
                        .toList());
    }

    /** The thinnest wall on the curve. */
    public Length thinnest() {
        return points.get(0).thickness();
    }

    /** The thickest wall on the curve. */
    public Length thickest() {
        return points.get(points.size() - 1).thickness();
    }

    /** Whether the curve reaches that thickness, both ends included. */
    public boolean covers(Length thickness) {
        return thinnest().compareTo(thickness) <= 0 && thickness.compareTo(thickest()) <= 0;
    }

    /**
     * The milliampere seconds for a thickness, read between points along the log scale.
     *
     * @throws TechniqueError for a thickness off either end of the curve
     */
    public BigDecimal masFor(Length thickness) {
        if (!covers(thickness)) {
            throw new TechniqueError(
                    "that thickness is off the end of the curve",
                    Context.of(
                            "kv",
                            kv,
                            "thickness_mm",
                            thickness.mm().toString(),
                            "from_mm",
                            thinnest().mm().toString(),
                            "to_mm",
                            thickest().mm().toString()));
        }
        Point lower = points.get(0);
        for (Point point : points) {
            if (point.thickness().equals(thickness)) {
                return point.mas();
            }
            if (point.thickness().compareTo(thickness) > 0) {
                return interpolate(lower, point, thickness);
            }
            lower = point;
        }
        return lower.mas();
    }

    private static BigDecimal interpolate(Point low, Point high, Length thickness) {
        BigDecimal span =
                BigDecimal.valueOf(high.thickness().um() - low.thickness().um());
        BigDecimal offset = BigDecimal.valueOf(thickness.um() - low.thickness().um());
        BigDecimal lowLog = Decimals.ln(low.mas());
        BigDecimal highLog = Decimals.ln(high.mas());
        BigDecimal rise = Decimals.divide(Decimals.multiply(Decimals.subtract(highLog, lowLog), offset), span);
        return Decimals.exp(Decimals.add(lowLog, rise));
    }
}
