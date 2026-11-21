package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * One exposure curve, valid for one isotope, material, film and density.
 *
 * <p>An exposure chart is a curve on semi log paper taped inside the door of the truck. Thickness
 * runs along the bottom and exposure in curie minutes runs up the side on a log scale, and the curve
 * is close enough to a straight line on that paper that a shooter reads it with a finger. So
 * reading between two points is linear in the logarithm, not in the value: plain linear
 * interpolation comes out long in the middle of every interval. And a chart cannot be extrapolated,
 * because the curve bends at both ends, so a thickness off the end gets a refusal and not a number.
 *
 * <p>Everything other than the four things the chart was drawn for is a correction applied
 * afterwards. Mixing a correction into the chart is how a shop ends up with nine charts that
 * disagree.
 *
 * @param key the key the chart is filed under
 * @param isotopeKey the isotope it was drawn for
 * @param materialKey the material it was drawn for
 * @param filmKey the film it was drawn for
 * @param density the density it was drawn for
 * @param referenceSfd the source to film distance it was drawn at
 * @param points the readings, thinnest first
 */
public record ExposureChart(
        String key,
        String isotopeKey,
        String materialKey,
        String filmKey,
        BigDecimal density,
        Length referenceSfd,
        List<ChartPoint> points) {

    /**
     * A chart; the points are copied.
     *
     * @throws ConfigurationError for fewer than two points, points out of thickness order, two at one
     *     thickness, or a curve that falls
     */
    public ExposureChart {
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new ConfigurationError("a chart needs at least two points", Context.of("key", key));
        }
        List<Length> thicknesses = points.stream().map(ChartPoint::thickness).toList();
        if (!thicknesses.equals(thicknesses.stream().sorted().toList())) {
            throw new ConfigurationError("chart points are out of order", Context.of("key", key));
        }
        if (new HashSet<>(thicknesses).size() != thicknesses.size()) {
            throw new ConfigurationError("a chart has two points at one thickness", Context.of("key", key));
        }
        List<BigDecimal> exposures =
                points.stream().map(ChartPoint::curieMinutes).toList();
        if (!exposures.equals(exposures.stream().sorted().toList())) {
            throw new ConfigurationError("a chart curve has to rise", Context.of("key", key));
        }
    }

    /**
     * Build a chart from the numbers as they are written on the card: the density, the distance in
     * inches, and each reading as a thickness in inches and an exposure in curie minutes.
     */
    public static ExposureChart fromInches(
            String key,
            String isotopeKey,
            String materialKey,
            String filmKey,
            String density,
            String referenceSfdInches,
            List<Map.Entry<String, String>> readings) {
        return new ExposureChart(
                key,
                isotopeKey,
                materialKey,
                filmKey,
                new BigDecimal(density),
                Length.fromInch(referenceSfdInches),
                readings.stream()
                        .map(reading ->
                                new ChartPoint(Length.fromInch(reading.getKey()), new BigDecimal(reading.getValue())))
                        .toList());
    }

    /** The thinnest wall on the chart. */
    public Length thinnest() {
        return points.get(0).thickness();
    }

    /** The thickest wall on the chart. */
    public Length thickest() {
        return points.get(points.size() - 1).thickness();
    }

    /** Whether the chart reaches that thickness, both ends included. */
    public boolean covers(Length thickness) {
        return thinnest().compareTo(thickness) <= 0 && thickness.compareTo(thickest()) <= 0;
    }

    /**
     * Curie minutes at the reference distance, film and density, read between points along the log
     * scale.
     *
     * @throws TechniqueError for a thickness off either end of the chart
     */
    public BigDecimal factorFor(Length thickness) {
        if (!covers(thickness)) {
            throw new TechniqueError(
                    "that thickness is off the end of the chart",
                    Context.of(
                            "chart",
                            key,
                            "thickness_mm",
                            thickness.mm().toString(),
                            "from_mm",
                            thinnest().mm().toString(),
                            "to_mm",
                            thickest().mm().toString()));
        }
        ChartPoint lower = points.get(0);
        for (ChartPoint point : points) {
            if (point.thickness().equals(thickness)) {
                return point.curieMinutes();
            }
            if (point.thickness().compareTo(thickness) > 0) {
                return interpolate(lower, point, thickness);
            }
            lower = point;
        }
        return lower.curieMinutes();
    }

    /**
     * The other way up the chart: the thickness an exposure reaches, to the micrometre below.
     *
     * @throws ValidationError for an exposure that is not positive
     * @throws TechniqueError for an exposure off either end of the chart
     */
    public Length thicknessFor(BigDecimal curieMinutes) {
        if (curieMinutes.signum() <= 0) {
            throw new ValidationError("an exposure has to be positive");
        }
        ChartPoint first = points.get(0);
        ChartPoint last = points.get(points.size() - 1);
        if (curieMinutes.compareTo(first.curieMinutes()) < 0 || curieMinutes.compareTo(last.curieMinutes()) > 0) {
            throw new TechniqueError(
                    "that exposure is off the end of the chart",
                    Context.of("chart", key, "curie_minutes", curieMinutes.toString()));
        }
        ChartPoint lower = first;
        for (ChartPoint point : points) {
            if (point.curieMinutes().compareTo(curieMinutes) == 0) {
                return point.thickness();
            }
            if (point.curieMinutes().compareTo(curieMinutes) > 0) {
                return invert(lower, point, curieMinutes);
            }
            lower = point;
        }
        return lower.thickness();
    }

    /** Linear in the logarithm, which is what the semi log paper does. */
    private static BigDecimal interpolate(ChartPoint low, ChartPoint high, Length thickness) {
        BigDecimal span =
                BigDecimal.valueOf(high.thickness().um() - low.thickness().um());
        BigDecimal offset = BigDecimal.valueOf(thickness.um() - low.thickness().um());
        BigDecimal lowLog = Decimals.ln(low.curieMinutes());
        BigDecimal highLog = Decimals.ln(high.curieMinutes());
        BigDecimal rise = Decimals.divide(Decimals.multiply(Decimals.subtract(highLog, lowLog), offset), span);
        return Decimals.exp(Decimals.add(lowLog, rise));
    }

    private static Length invert(ChartPoint low, ChartPoint high, BigDecimal curieMinutes) {
        BigDecimal lowLog = Decimals.ln(low.curieMinutes());
        BigDecimal highLog = Decimals.ln(high.curieMinutes());
        BigDecimal fraction = Decimals.divide(
                Decimals.subtract(Decimals.ln(curieMinutes), lowLog), Decimals.subtract(highLog, lowLog));
        BigDecimal span =
                BigDecimal.valueOf(high.thickness().um() - low.thickness().um());
        return new Length(Decimals.truncate(
                Decimals.add(BigDecimal.valueOf(low.thickness().um()), Decimals.multiply(span, fraction))));
    }
}
