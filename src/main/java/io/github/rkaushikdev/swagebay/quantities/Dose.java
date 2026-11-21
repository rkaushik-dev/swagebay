package io.github.rkaushikdev.swagebay.quantities;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import io.github.rkaushikdev.swagebay.util.Repr;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An accumulated dose equivalent, to the nanosievert.
 *
 * <p>The regulator writes microsieverts and the older paperwork writes millirem. Both come out of
 * the same store without drifting, because nanosieverts hold every value either system quotes.
 *
 * @param nsv the dose in whole nanosieverts, never negative
 */
public record Dose(long nsv) implements Comparable<Dose>, Repr.Quoted {

    /** Nanosieverts in a microsievert. */
    public static final long NSV_PER_USV = 1_000;

    /** Nanosieverts in a millisievert. */
    public static final long NSV_PER_MSV = 1_000_000;

    /** No dose at all. */
    public static final Dose ZERO = new Dose(0);

    private static final Pattern PATTERN = Pattern.compile(
            "\\s*(?<value>\\d+(?:\\.\\d+)?)\\s*(?<unit>nsv|usv|msv|sv|mrem|rem)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, BigDecimal> MULTIPLIERS = Map.of(
            "nsv", BigDecimal.ONE,
            "usv", BigDecimal.valueOf(NSV_PER_USV),
            "msv", BigDecimal.valueOf(NSV_PER_MSV),
            "sv", BigDecimal.valueOf(NSV_PER_MSV * 1000),
            "mrem", BigDecimal.valueOf(NSV_PER_USV * 10),
            "rem", BigDecimal.valueOf(NSV_PER_MSV * 10));

    /**
     * A dose of that many nanosieverts.
     *
     * @throws ValidationError when it is negative
     */
    public Dose {
        if (nsv < 0) {
            throw new ValidationError("a dose cannot be negative", Context.of("given", nsv));
        }
    }

    /** A dose from microsieverts, to the nearest nanosievert. */
    public static Dose fromUsv(BigDecimal usv) {
        return new Dose(roundHalfUp(Decimals.multiply(usv, BigDecimal.valueOf(NSV_PER_USV))));
    }

    /** A dose from microsieverts written as text. */
    public static Dose fromUsv(String usv) {
        return fromUsv(Decimals.of(usv));
    }

    /** A dose from whole microsieverts. */
    public static Dose fromUsv(long usv) {
        return fromUsv(BigDecimal.valueOf(usv));
    }

    /** A dose from millisieverts, to the nearest nanosievert. */
    public static Dose fromMsv(BigDecimal msv) {
        return new Dose(roundHalfUp(Decimals.multiply(msv, BigDecimal.valueOf(NSV_PER_MSV))));
    }

    /** A dose from millisieverts written as text. */
    public static Dose fromMsv(String msv) {
        return fromMsv(Decimals.of(msv));
    }

    /** A dose from whole millisieverts. */
    public static Dose fromMsv(long msv) {
        return fromMsv(BigDecimal.valueOf(msv));
    }

    /** A dose from millirem, to the nearest nanosievert. */
    public static Dose fromMrem(BigDecimal mrem) {
        return new Dose(roundHalfUp(Decimals.multiply(mrem, BigDecimal.valueOf(NSV_PER_USV * 10))));
    }

    /** A dose from millirem written as text. */
    public static Dose fromMrem(String mrem) {
        return fromMrem(Decimals.of(mrem));
    }

    /** A dose from whole millirem. */
    public static Dose fromMrem(long mrem) {
        return fromMrem(BigDecimal.valueOf(mrem));
    }

    /** Read a dose: a number and one of {@code nSv}, {@code uSv}, {@code mSv}, {@code Sv}, {@code mrem} or {@code rem}. */
    public static Dose parse(String text) {
        Matcher match = PATTERN.matcher(text);
        if (!match.matches()) {
            throw new ValidationError("not a dose", Context.of("text", text));
        }
        BigDecimal multiplier = MULTIPLIERS.get(match.group("unit").toLowerCase(Locale.ROOT));
        return new Dose(roundHalfUp(Decimals.multiply(new BigDecimal(match.group("value")), multiplier)));
    }

    /** The dose in microsieverts. */
    public BigDecimal usv() {
        return Decimals.divide(nsv, NSV_PER_USV);
    }

    /** The dose in millisieverts. */
    public BigDecimal msv() {
        return Decimals.divide(nsv, NSV_PER_MSV);
    }

    /** The dose in millirem. */
    public BigDecimal mrem() {
        return Decimals.divide(nsv, NSV_PER_USV * 10);
    }

    /** Both doses taken. */
    public Dose plus(Dose other) {
        return new Dose(Math.addExact(nsv, other.nsv));
    }

    /**
     * This dose less that one.
     *
     * @throws ValidationError when that would go below zero
     */
    public Dose minus(Dose other) {
        if (other.nsv > nsv) {
            throw new ValidationError("a dose cannot go below zero", Context.of("left", nsv, "right", other.nsv));
        }
        return new Dose(nsv - other.nsv);
    }

    /**
     * Scaled by a factor, to the nearest nanosievert.
     *
     * @throws ValidationError for a negative factor
     */
    public Dose times(BigDecimal factor) {
        if (factor.signum() < 0) {
            throw new ValidationError("cannot scale a dose by a negative factor", Context.of("factor", factor));
        }
        return new Dose(roundHalfUp(Decimals.multiply(BigDecimal.valueOf(nsv), factor)));
    }

    /** Scaled by a whole factor. */
    public Dose times(long factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a dose by a negative factor", Context.of("factor", factor));
        }
        return times(BigDecimal.valueOf(factor));
    }

    /** Scaled by a factor given as a double, read as the decimal its text spells. */
    public Dose times(double factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a dose by a negative factor", Context.of("factor", factor));
        }
        return times(Floats.decimal(factor));
    }

    /**
     * The share of a limit this dose has used.
     *
     * @throws ValidationError against a zero limit
     */
    public BigDecimal fractionOf(Dose limit) {
        if (limit.nsv == 0) {
            throw new ValidationError("cannot take a fraction of a zero limit");
        }
        return Decimals.divide(nsv, limit.nsv);
    }

    /** Microsieverts to that many places, ties away from zero: {@code 500.0 uSv}. */
    public String formatUsv(int places) {
        return Decimals.halfUp(usv(), places) + " uSv";
    }

    /** Microsieverts to one place. */
    public String formatUsv() {
        return formatUsv(1);
    }

    /** Millirem to that many places, ties away from zero. */
    public String formatMrem(int places) {
        return Decimals.halfUp(mrem(), places) + " mrem";
    }

    /** Millirem to one place. */
    public String formatMrem() {
        return formatMrem(1);
    }

    /** Millisieverts to that many places, ties away from zero. */
    public String formatMsv(int places) {
        return Decimals.halfUp(msv(), places) + " mSv";
    }

    /** Millisieverts to two places. */
    public String formatMsv() {
        return formatMsv(2);
    }

    @Override
    public int compareTo(Dose other) {
        return Long.compare(nsv, other.nsv);
    }

    /** Microsieverts to one place. */
    @Override
    public String toString() {
        return formatUsv();
    }

    @Override
    public String repr() {
        return "Dose(" + nsv + ")";
    }

    static long roundHalfUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
