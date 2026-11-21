package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Repair rate over time, which is the number a client watches and a shop dreads.
 *
 * <p>A repair rate is repaired welds over welds shot and it is only meaningful with its denominator
 * attached, so every figure here carries the count it was computed from, and anything that hides
 * the count is a figure somebody will misread.
 *
 * <p>The band is the other half. A shop knows what its own rate normally is, and the useful question
 * is not whether this month is above zero but whether it is outside the band the shop normally sits
 * in. That band is set from the shop's own history rather than from a target, because a target is a
 * management number and the history is a measurement.
 *
 * <p>Per welder is where this gets political and the code stays out of it. It reports the rate and
 * the count. It does not rank, it does not flag, and it does not compute a rate on a denominator too
 * small to mean anything, because a welder with three welds and one repair is not a thirty three
 * percent welder.
 */
public final class Statistics {

    /** Below this many welds shot a rate is not reported, because it is one weld either way. */
    public static final int MINIMUM_DENOMINATOR = 20;

    private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("1.5");
    private static final int DEFAULT_RUN = 3;
    private static final int DEFAULT_PLACES = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * A welder's raw counts, before they are a rate.
     *
     * @param repaired the welds repaired
     * @param shot the welds shot
     */
    public record Tally(int repaired, int shot) {}

    private Statistics() {}

    /** Pool several periods by adding their counts, which is not the average of their rates. */
    public static Rate combine(Collection<Rate> rates) {
        int repaired = 0;
        int shot = 0;
        for (Rate rate : rates) {
            repaired = Math.addExact(repaired, rate.repaired());
            shot = Math.addExact(shot, rate.shot());
        }
        return new Rate(repaired, shot);
    }

    /** {@link #bandFrom(Collection, BigDecimal)} at a spread of one and a half. */
    public static Band bandFrom(Collection<Period> periods) {
        return bandFrom(periods, DEFAULT_SPREAD);
    }

    /**
     * Set the band from the shop's own history rather than from a target: the pooled rate of the
     * reportable months, divided and multiplied by the spread.
     *
     * <p>The spread is a multiple either side of the pooled rate rather than a standard deviation. A
     * repair rate is a proportion on a small denominator and its distribution is nothing like normal,
     * so a standard deviation from a handful of months is a number with a decimal point and no
     * meaning. A multiple is crude, it is understood by everybody who reads it, and it does not
     * pretend.
     *
     * @throws ValidationError for a spread of one or less, or no month with enough welds to rate
     */
    public static Band bandFrom(Collection<Period> periods, BigDecimal spread) {
        if (spread.compareTo(BigDecimal.ONE) <= 0) {
            throw new ValidationError("a spread is greater than one", Context.of("spread", spread.toString()));
        }
        List<Period> reportable =
                periods.stream().filter(period -> period.rate().isReportable()).toList();
        if (reportable.isEmpty()) {
            throw new ValidationError("there is not enough history to set a band");
        }
        BigDecimal pooled =
                combine(reportable.stream().map(Period::rate).toList()).value();
        return new Band(pooled, Decimals.divide(pooled, spread), Decimals.multiply(pooled, spread));
    }

    /** Months outside the band, in the order given, ignoring the ones with too few welds to rate. */
    public static List<Period> excursions(Collection<Period> periods, Band band) {
        return periods.stream()
                .filter(period -> period.rate().isReportable() && !band.contains(period.rate()))
                .toList();
    }

    /** {@link #rising(Collection, int)} over the last three reportable months. */
    public static boolean rising(Collection<Period> periods) {
        return rising(periods, DEFAULT_RUN);
    }

    /**
     * Whether the last few reportable months, in calendar order, each beat the one before.
     *
     * @throws ValidationError for a run shorter than two months
     */
    public static boolean rising(Collection<Period> periods, int run) {
        if (run < 2) {
            throw new ValidationError("a run is at least two months", Context.of("run", run));
        }
        List<Period> ordered = periods.stream()
                .sorted(Comparator.comparing(Period::startsOn))
                .filter(period -> period.rate().isReportable())
                .toList();
        if (ordered.size() < run) {
            return false;
        }
        List<Period> recent = ordered.subList(ordered.size() - run, ordered.size());
        for (int i = 1; i < recent.size(); i++) {
            BigDecimal earlier = recent.get(i - 1).rate().value();
            BigDecimal later = recent.get(i).rate().value();
            if (later.compareTo(earlier) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Per welder, by welder in order, with the ones on too few welds still carrying their counts.
     *
     * @throws ValidationError for counts that are not a rate
     */
    public static Map<String, Rate> byWelder(Map<String, Tally> counts) {
        Map<String, Rate> rates = new LinkedHashMap<>();
        new TreeMap<>(counts).forEach((welder, tally) -> rates.put(welder, new Rate(tally.repaired(), tally.shot())));
        return Collections.unmodifiableMap(rates);
    }

    /** The welders with enough welds to rate, in order. */
    public static List<String> reportableWelders(Map<String, Tally> counts) {
        return byWelder(counts).entrySet().stream()
                .filter(entry -> entry.getValue().isReportable())
                .map(Map.Entry::getKey)
                .toList();
    }

    /** {@link #asPercentage(Rate, int)} to two places. */
    public static BigDecimal asPercentage(Rate rate) {
        return asPercentage(rate, DEFAULT_PLACES);
    }

    /** The rate as a percentage to that many places, ties away from zero, the way a report rounds. */
    public static BigDecimal asPercentage(Rate rate, int places) {
        return Decimals.halfUp(Decimals.multiply(rate.value(), HUNDRED), places);
    }

    /** A fraction as a percentage to two places, a tie going to the even digit: {@code 4.00%}. */
    static String percent(BigDecimal fraction) {
        return Decimals.halfEven(fraction.movePointRight(2), 2).toPlainString() + "%";
    }
}
