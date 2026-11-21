package io.github.rkaushikdev.swagebay.quantities;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import io.github.rkaushikdev.swagebay.util.Repr;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A span of time, to the millisecond.
 *
 * <p>An exposure is quoted in minutes and seconds and written on the technique sheet the way the
 * crew will set the timer: one minute forty. A crank out is quoted in seconds, a stay time inside
 * a boundary in minutes. All of them are the same quantity and none of them is a bare number,
 * because a number that means seconds and one that means minutes look identical right up until
 * somebody multiplies one by a gamma constant. Timers on exposure devices resolve to a second, so
 * a whole millisecond is more resolution than the equipment has and the arithmetic stays exact.
 *
 * @param ms the span in whole milliseconds, never negative
 */
public record Duration(long ms) implements Comparable<Duration>, Repr.Quoted {

    /** Milliseconds in a second. */
    public static final long MS_PER_SECOND = 1000;

    /** Milliseconds in a minute. */
    public static final long MS_PER_MINUTE = 60_000;

    /** Milliseconds in an hour. */
    public static final long MS_PER_HOUR = 3_600_000;

    /** No time at all. */
    public static final Duration ZERO = new Duration(0);

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern CLOCK = Pattern.compile(
            "\\s*(?:(?<h>\\d+):)?(?<m>\\d{1,2}):(?<s>\\d{1,2}(?:\\.\\d+)?)\\s*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMPOUND = Pattern.compile(
            "\\s*(?:(?<h>\\d+(?:\\.\\d+)?)\\s*h)?\\s*"
                    + "(?:(?<m>\\d+(?:\\.\\d+)?)\\s*m(?!s))?\\s*"
                    + "(?:(?<s>\\d+(?:\\.\\d+)?)\\s*s)?\\s*",
            FLAGS);

    /**
     * A span of that many milliseconds.
     *
     * @throws ValidationError when it is negative
     */
    public Duration {
        if (ms < 0) {
            throw new ValidationError("a duration cannot be negative", Context.of("given", ms));
        }
    }

    /** A span of seconds, to the nearest millisecond. */
    public static Duration fromSeconds(BigDecimal seconds) {
        return new Duration(roundHalfUp(Decimals.multiply(seconds, BigDecimal.valueOf(MS_PER_SECOND))));
    }

    /** A span of seconds written as text. */
    public static Duration fromSeconds(String seconds) {
        return fromSeconds(Decimals.of(seconds));
    }

    /** A span of whole seconds. */
    public static Duration fromSeconds(long seconds) {
        return fromSeconds(BigDecimal.valueOf(seconds));
    }

    /** A span of minutes, to the nearest millisecond. */
    public static Duration fromMinutes(BigDecimal minutes) {
        return new Duration(roundHalfUp(Decimals.multiply(minutes, BigDecimal.valueOf(MS_PER_MINUTE))));
    }

    /** A span of minutes written as text. */
    public static Duration fromMinutes(String minutes) {
        return fromMinutes(Decimals.of(minutes));
    }

    /** A span of whole minutes. */
    public static Duration fromMinutes(long minutes) {
        return fromMinutes(BigDecimal.valueOf(minutes));
    }

    /** A span of hours, to the nearest millisecond. */
    public static Duration fromHours(BigDecimal hours) {
        return new Duration(roundHalfUp(Decimals.multiply(hours, BigDecimal.valueOf(MS_PER_HOUR))));
    }

    /** A span of hours written as text. */
    public static Duration fromHours(String hours) {
        return fromHours(Decimals.of(hours));
    }

    /** A span of whole hours. */
    public static Duration fromHours(long hours) {
        return fromHours(BigDecimal.valueOf(hours));
    }

    /**
     * Read a duration written any of the three ways the office writes it: clock notation
     * ({@code 1:40}, {@code 1:02:30}, {@code 2:07.5}) or hours, minutes and seconds
     * ({@code 1h 30m}, {@code 1m 40s}, {@code 90s}).
     */
    public static Duration parse(String text) {
        Matcher clock = CLOCK.matcher(text);
        if (clock.matches()) {
            BigDecimal hours = clock.group("h") == null ? BigDecimal.ZERO : new BigDecimal(clock.group("h"));
            BigDecimal minutes = new BigDecimal(clock.group("m"));
            BigDecimal seconds = new BigDecimal(clock.group("s"));
            if (minutes.compareTo(BigDecimal.valueOf(59)) > 0 || seconds.compareTo(BigDecimal.valueOf(60)) >= 0) {
                throw new ValidationError("clock time out of range", Context.of("text", text));
            }
            return new Duration(roundHalfUp(total(hours, minutes, seconds)));
        }
        Matcher compound = COMPOUND.matcher(text);
        if (!compound.matches() || !(present(compound, "h") || present(compound, "m") || present(compound, "s"))) {
            throw new ValidationError("not a duration", Context.of("text", text));
        }
        return new Duration(roundHalfUp(total(part(compound, "h"), part(compound, "m"), part(compound, "s"))));
    }

    /** The span in seconds. */
    public BigDecimal seconds() {
        return Decimals.divide(ms, MS_PER_SECOND);
    }

    /** The span in minutes. */
    public BigDecimal minutes() {
        return Decimals.divide(ms, MS_PER_MINUTE);
    }

    /** The span in hours. */
    public BigDecimal hours() {
        return Decimals.divide(ms, MS_PER_HOUR);
    }

    /** One span after the other. */
    public Duration plus(Duration other) {
        return new Duration(Math.addExact(ms, other.ms));
    }

    /**
     * This span less that one.
     *
     * @throws ValidationError when that would go below zero
     */
    public Duration minus(Duration other) {
        if (other.ms > ms) {
            throw new ValidationError("a duration cannot go below zero", Context.of("left", ms, "right", other.ms));
        }
        return new Duration(ms - other.ms);
    }

    /**
     * Scaled by a factor, to the nearest millisecond.
     *
     * @throws ValidationError for a negative factor
     */
    public Duration times(BigDecimal factor) {
        if (factor.signum() < 0) {
            throw new ValidationError("cannot scale a duration by a negative factor", Context.of("factor", factor));
        }
        return new Duration(roundHalfUp(Decimals.multiply(BigDecimal.valueOf(ms), factor)));
    }

    /** Scaled by a whole factor. */
    public Duration times(long factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a duration by a negative factor", Context.of("factor", factor));
        }
        return times(BigDecimal.valueOf(factor));
    }

    /** Scaled by a factor given as a double, read as the decimal its text spells. */
    public Duration times(double factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale a duration by a negative factor", Context.of("factor", factor));
        }
        return times(Floats.decimal(factor));
    }

    /**
     * Divided into that many parts, to the nearest millisecond.
     *
     * @throws ValidationError for a divisor of zero or less
     */
    public Duration dividedBy(BigDecimal divisor) {
        if (divisor.signum() <= 0) {
            throw new ValidationError("cannot divide a duration by that", Context.of("divisor", divisor));
        }
        return new Duration(roundHalfUp(Decimals.divide(BigDecimal.valueOf(ms), divisor)));
    }

    /** Divided by a whole number. */
    public Duration dividedBy(long divisor) {
        if (divisor <= 0) {
            throw new ValidationError("cannot divide a duration by that", Context.of("divisor", divisor));
        }
        return dividedBy(BigDecimal.valueOf(divisor));
    }

    /**
     * This span over that one, the exposure factor between them.
     *
     * @throws ValidationError against a zero duration
     */
    public BigDecimal ratio(Duration other) {
        if (other.ms == 0) {
            throw new ValidationError("cannot take a ratio against a zero duration");
        }
        return Decimals.divide(ms, other.ms);
    }

    /** Up to the next whole second, because a timer that only takes whole seconds must not be set short. */
    public Duration roundUpToSecond() {
        long remainder = ms % MS_PER_SECOND;
        return remainder == 0 ? this : new Duration((ms / MS_PER_SECOND + 1) * MS_PER_SECOND);
    }

    /** What gets written in the time box on the technique sheet: {@code 1:40}, or {@code 1:15:00} past an hour. */
    public String asTimer() {
        long total = roundUpToSecond().ms / MS_PER_SECOND;
        long seconds = total % 60;
        long minutes = (total / 60) % 60;
        long hours = total / 3600;
        if (hours != 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    @Override
    public int compareTo(Duration other) {
        return Long.compare(ms, other.ms);
    }

    /** The timer form. */
    @Override
    public String toString() {
        return asTimer();
    }

    @Override
    public String repr() {
        return "Duration(" + ms + ")";
    }

    private static BigDecimal total(BigDecimal hours, BigDecimal minutes, BigDecimal seconds) {
        BigDecimal sum = Decimals.add(
                Decimals.multiply(hours, BigDecimal.valueOf(MS_PER_HOUR)),
                Decimals.multiply(minutes, BigDecimal.valueOf(MS_PER_MINUTE)));
        return Decimals.add(sum, Decimals.multiply(seconds, BigDecimal.valueOf(MS_PER_SECOND)));
    }

    private static boolean present(Matcher match, String group) {
        String found = match.group(group);
        return found != null && !found.isEmpty();
    }

    private static BigDecimal part(Matcher match, String group) {
        return present(match, group) ? new BigDecimal(match.group(group)) : BigDecimal.ZERO;
    }

    private static long roundHalfUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
