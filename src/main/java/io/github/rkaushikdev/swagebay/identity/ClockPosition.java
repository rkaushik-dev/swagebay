package io.github.rkaushikdev.swagebay.identity;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A position round a pipe, stored as minutes past twelve.
 *
 * <p>A crew writes 1200, 12:00, 12 or top for the same place on the pipe, and a report that
 * carries all four is a report nobody can sort, so every one of them reads to the same value.
 *
 * @param minutes minutes clockwise from twelve, 0 to 719
 */
public record ClockPosition(int minutes) implements Comparable<ClockPosition> {

    private static final Pattern SHAPE =
            Pattern.compile("(?<hour>\\d{1,2})(?::?(?<minute>[0-5]\\d))?", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String, Integer> NAMED = Map.of("top", 0, "bottom", 360, "12", 0, "6", 360);
    private static final int DIAL = 720;

    /**
     * A clock position.
     *
     * @throws ValidationError outside twelve hours
     */
    public ClockPosition {
        if (minutes < 0 || minutes >= DIAL) {
            throw new ValidationError("a clock position is inside twelve hours", Context.of("minutes", minutes));
        }
    }

    /** Read a position: {@code 3:00}, {@code 300}, {@code 3}, {@code top} or {@code bottom}. */
    public static ClockPosition parse(String text) {
        String cleaned = Text.strip(text).toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(cleaned);
        if (named != null) {
            return new ClockPosition(named);
        }
        Matcher match = SHAPE.matcher(cleaned);
        if (!match.matches()) {
            throw new ValidationError("not a clock position", Context.of("text", text));
        }
        int hour = Integer.parseInt(match.group("hour"));
        int minute = match.group("minute") == null ? 0 : Integer.parseInt(match.group("minute"));
        if (hour < 1 || hour > 12) {
            throw new ValidationError("a clock hour is one to twelve", Context.of("hour", hour));
        }
        return new ClockPosition(((hour % 12) * 60 + minute) % DIAL);
    }

    /** The position at that many degrees clockwise from twelve, wrapped into one turn. */
    public static ClockPosition fromDegrees(BigDecimal degrees) {
        BigDecimal wrapped = degrees.remainder(BigDecimal.valueOf(360));
        return new ClockPosition(Math.toIntExact(Decimals.truncate(Decimals.multiply(wrapped, BigDecimal.TWO))));
    }

    /** Degrees clockwise from twelve. */
    public BigDecimal degrees() {
        return Decimals.divide(minutes, 2);
    }

    /** Minutes clockwise from here to there. */
    public int arcTo(ClockPosition other) {
        return Math.floorMod(other.minutes - minutes, DIAL);
    }

    @Override
    public int compareTo(ClockPosition other) {
        return Integer.compare(minutes, other.minutes);
    }

    /** The dial form, {@code 12:00} for the top. */
    @Override
    public String toString() {
        int hour = minutes / 60;
        return String.format(Locale.ROOT, "%d:%02d", hour == 0 ? 12 : hour, minutes % 60);
    }
}
