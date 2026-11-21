package io.github.rkaushikdev.swagebay.quantities;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import io.github.rkaushikdev.swagebay.util.Repr;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dose equivalent rate, to the nanosievert in an hour.
 *
 * <p>The survey meters in the trucks read milliroentgen an hour, because they were bought in the
 * nineties and nobody replaces a working meter, and the regulator writes microsieverts an hour.
 * The conversion is the field convention, one milliroentgen to ten microsieverts. It is not a
 * physical identity, but every procedure, posting rule and meter scale card is written on it, so
 * that is what the arithmetic reproduces.
 *
 * @param nsvPerHour the rate in whole nanosieverts an hour, never negative
 */
public record DoseRate(long nsvPerHour) implements Comparable<DoseRate>, Repr.Quoted {

    /** Microsieverts to the milliroentgen, by field convention. */
    public static final BigDecimal USV_PER_MR = BigDecimal.TEN;

    /** No dose rate at all. */
    public static final DoseRate ZERO = new DoseRate(0);

    private static final BigDecimal NSV_PER_USV = BigDecimal.valueOf(Dose.NSV_PER_USV);
    private static final BigDecimal NSV_PER_MSV = BigDecimal.valueOf(Dose.NSV_PER_MSV);
    private static final BigDecimal NSV_PER_MR = NSV_PER_USV.multiply(USV_PER_MR);

    private static final Pattern PATTERN = Pattern.compile(
            "\\s*(?<value>\\d+(?:\\.\\d+)?)\\s*(?<unit>nsv|usv|msv|mr|r)\\s*/\\s*h(?:r)?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, BigDecimal> MULTIPLIERS = Map.of(
            "nsv", BigDecimal.ONE,
            "usv", NSV_PER_USV,
            "msv", NSV_PER_MSV,
            "mr", NSV_PER_MR,
            "r", NSV_PER_MR.multiply(BigDecimal.valueOf(1000)));

    /**
     * A rate of that many nanosieverts an hour.
     *
     * @throws ValidationError when it is negative
     */
    public DoseRate {
        if (nsvPerHour < 0) {
            throw new ValidationError("a dose rate cannot be negative", Context.of("given", nsvPerHour));
        }
    }

    /** A rate from microsieverts an hour, to the nearest nanosievert. */
    public static DoseRate fromUsvPerHour(BigDecimal usv) {
        return new DoseRate(Dose.roundHalfUp(Decimals.multiply(usv, NSV_PER_USV)));
    }

    /** A rate from microsieverts an hour written as text. */
    public static DoseRate fromUsvPerHour(String usv) {
        return fromUsvPerHour(Decimals.of(usv));
    }

    /** A rate from whole microsieverts an hour. */
    public static DoseRate fromUsvPerHour(long usv) {
        return fromUsvPerHour(BigDecimal.valueOf(usv));
    }

    /** A rate from millisieverts an hour, to the nearest nanosievert. */
    public static DoseRate fromMsvPerHour(BigDecimal msv) {
        return new DoseRate(Dose.roundHalfUp(Decimals.multiply(msv, NSV_PER_MSV)));
    }

    /** A rate from millisieverts an hour written as text. */
    public static DoseRate fromMsvPerHour(String msv) {
        return fromMsvPerHour(Decimals.of(msv));
    }

    /** A rate from whole millisieverts an hour. */
    public static DoseRate fromMsvPerHour(long msv) {
        return fromMsvPerHour(BigDecimal.valueOf(msv));
    }

    /** A rate from milliroentgen an hour, which is what the meter in the truck actually reads. */
    public static DoseRate fromMrPerHour(BigDecimal mr) {
        return new DoseRate(Dose.roundHalfUp(Decimals.multiply(Decimals.multiply(mr, USV_PER_MR), NSV_PER_USV)));
    }

    /** A rate from milliroentgen an hour written as text. */
    public static DoseRate fromMrPerHour(String mr) {
        return fromMrPerHour(Decimals.of(mr));
    }

    /** A rate from whole milliroentgen an hour. */
    public static DoseRate fromMrPerHour(long mr) {
        return fromMrPerHour(BigDecimal.valueOf(mr));
    }

    /** Read a rate off a survey sheet: {@code 2 mR/h}, {@code 2 mR/hr}, {@code 20 uSv/h}, {@code 1 R / h}. */
    public static DoseRate parse(String text) {
        Matcher match = PATTERN.matcher(text);
        if (!match.matches()) {
            throw new ValidationError("not a dose rate", Context.of("text", text));
        }
        BigDecimal multiplier = MULTIPLIERS.get(match.group("unit").toLowerCase(Locale.ROOT));
        return new DoseRate(Dose.roundHalfUp(Decimals.multiply(new BigDecimal(match.group("value")), multiplier)));
    }

    /** The rate in microsieverts an hour. */
    public BigDecimal usvPerHour() {
        return Decimals.divide(BigDecimal.valueOf(nsvPerHour), NSV_PER_USV);
    }

    /** The rate in millisieverts an hour. */
    public BigDecimal msvPerHour() {
        return Decimals.divide(BigDecimal.valueOf(nsvPerHour), NSV_PER_MSV);
    }

    /** The rate in milliroentgen an hour. */
    public BigDecimal mrPerHour() {
        return Decimals.divide(BigDecimal.valueOf(nsvPerHour), NSV_PER_MR);
    }

    /** Both rates at once, as two sources side by side give. */
    public DoseRate plus(DoseRate other) {
        return new DoseRate(Math.addExact(nsvPerHour, other.nsvPerHour));
    }

    /**
     * Scaled by a factor, to the nearest nanosievert an hour.
     *
     * @throws ValidationError for a negative factor
     */
    public DoseRate times(BigDecimal factor) {
        if (factor.signum() < 0) {
            throw new ValidationError("cannot scale a dose rate by a negative factor", Context.of("factor", factor));
        }
        return new DoseRate(Dose.roundHalfUp(Decimals.multiply(BigDecimal.valueOf(nsvPerHour), factor)));
    }

    /** Scaled by a whole factor. */
    public DoseRate times(long factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a dose rate by a negative factor", Context.of("factor", factor));
        }
        return times(BigDecimal.valueOf(factor));
    }

    /** Scaled by a factor given as a double, read as the decimal its text spells. */
    public DoseRate times(double factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a dose rate by a negative factor", Context.of("factor", factor));
        }
        return times(Floats.decimal(factor));
    }

    /** The dose somebody standing here for that long would take, to the nearest nanosievert. */
    public Dose over(Duration span) {
        BigDecimal hours = Decimals.divide(span.ms(), Duration.MS_PER_HOUR);
        return new Dose(Dose.roundHalfUp(Decimals.multiply(BigDecimal.valueOf(nsvPerHour), hours)));
    }

    /**
     * How long the budget lasts at this rate, truncated to the millisecond and never rounded up.
     *
     * @throws ValidationError at a zero rate, where the stay is unbounded
     */
    public Duration stayTime(Dose budget) {
        if (nsvPerHour == 0) {
            throw new ValidationError("stay time is unbounded at a zero dose rate");
        }
        BigDecimal hours = Decimals.divide(budget.nsv(), nsvPerHour);
        return new Duration(Decimals.truncate(Decimals.multiply(hours, BigDecimal.valueOf(Duration.MS_PER_HOUR))));
    }

    /** Microsieverts an hour to that many places, ties away from zero: {@code 20.0 uSv/h}. */
    public String formatUsvPerHour(int places) {
        return Decimals.halfUp(usvPerHour(), places) + " uSv/h";
    }

    /** Microsieverts an hour to one place. */
    public String formatUsvPerHour() {
        return formatUsvPerHour(1);
    }

    /** Milliroentgen an hour to that many places, ties away from zero: {@code 2.00 mR/h}. */
    public String formatMrPerHour(int places) {
        return Decimals.halfUp(mrPerHour(), places) + " mR/h";
    }

    /** Milliroentgen an hour to two places. */
    public String formatMrPerHour() {
        return formatMrPerHour(2);
    }

    @Override
    public int compareTo(DoseRate other) {
        return Long.compare(nsvPerHour, other.nsvPerHour);
    }

    /** Microsieverts an hour to one place. */
    @Override
    public String toString() {
        return formatUsvPerHour();
    }

    @Override
    public String repr() {
        return "DoseRate(" + nsvPerHour + ")";
    }
}
