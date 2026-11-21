package io.github.rkaushikdev.swagebay.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moments written as text and read back.
 *
 * <p>The written form is ISO 8601 with the seconds always present and the fraction only when there
 * is one, to the microsecond: {@code 2024-05-01T06:30:00}, {@code 2024-05-01T06:30:00.250000}, and
 * an offset as {@code +00:00}. That form is what the records, the exports and the audit trail
 * carry, so a moment written by one version reads back in any other.
 */
public final class Times {

    private static final Pattern MOMENT =
            Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2})(?::(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,9}))?)?)?)?"
                    + "(Z|[+-]\\d{2}:?\\d{2}(?::?\\d{2})?)?");

    private Times() {}

    /** The ISO form of a wall clock moment, seconds always written. */
    public static String iso(LocalDateTime moment) {
        LocalDateTime micros = moment.truncatedTo(ChronoUnit.MICROS);
        String base = String.format(
                Locale.ROOT,
                "%04d-%02d-%02dT%02d:%02d:%02d",
                micros.getYear(),
                micros.getMonthValue(),
                micros.getDayOfMonth(),
                micros.getHour(),
                micros.getMinute(),
                micros.getSecond());
        int fraction = micros.getNano() / 1000;
        return fraction == 0 ? base : base + String.format(Locale.ROOT, ".%06d", fraction);
    }

    /** The ISO form of an instant, with its offset as {@code +HH:MM}. */
    public static String iso(OffsetDateTime moment) {
        return iso(moment.toLocalDateTime()) + offset(moment.getOffset());
    }

    /** The ISO form of a day. */
    public static String iso(LocalDate day) {
        return day.toString();
    }

    /** The ISO form of a time of day, seconds always written. */
    public static String iso(LocalTime time) {
        return iso(LocalDateTime.of(LocalDate.EPOCH, time)).substring(11);
    }

    /** The form for people: a space rather than the {@code T}. */
    public static String text(LocalDateTime moment) {
        return iso(moment).replace('T', ' ');
    }

    /** A wall clock moment from its ISO text; a bare day is its midnight. */
    public static LocalDateTime parse(String text) {
        Matcher match = matcher(text);
        if (match.group(8) != null) {
            throw new DateTimeParseException("an offset was given where a wall clock time was expected", text, 0);
        }
        return local(match, text);
    }

    /** An instant from its ISO text, if the text carries an offset. */
    public static Optional<OffsetDateTime> parseInstant(String text) {
        Matcher match = matcher(text);
        if (match.group(8) == null) {
            return Optional.empty();
        }
        return Optional.of(local(match, text).atOffset(zone(match.group(8))));
    }

    /** Whether the text is a moment in ISO form, with or without an offset. */
    public static boolean isMoment(String text) {
        return MOMENT.matcher(text).matches();
    }

    private static Matcher matcher(String text) {
        Matcher match = MOMENT.matcher(text);
        if (!match.matches()) {
            throw new DateTimeParseException("not an ISO moment", text, 0);
        }
        return match;
    }

    private static LocalDateTime local(Matcher match, String text) {
        try {
            LocalDate day = LocalDate.of(number(match, 1), number(match, 2), number(match, 3));
            int hour = match.group(4) == null ? 0 : number(match, 4);
            int minute = match.group(5) == null ? 0 : number(match, 5);
            int second = match.group(6) == null ? 0 : number(match, 6);
            int nanos = 0;
            if (match.group(7) != null) {
                String digits = (match.group(7) + "000000000").substring(0, 9);
                nanos = Integer.parseInt(digits);
            }
            return LocalDateTime.of(day, LocalTime.of(hour, minute, second, nanos));
        } catch (java.time.DateTimeException invalid) {
            throw new DateTimeParseException(invalid.getMessage(), text, 0, invalid);
        }
    }

    private static int number(Matcher match, int group) {
        return Integer.parseInt(match.group(group));
    }

    private static ZoneOffset zone(String text) {
        if (text.equals("Z")) {
            return ZoneOffset.UTC;
        }
        String digits = text.replace(":", "");
        int sign = digits.charAt(0) == '-' ? -1 : 1;
        int hours = Integer.parseInt(digits.substring(1, 3));
        int minutes = Integer.parseInt(digits.substring(3, 5));
        int seconds = digits.length() > 5 ? Integer.parseInt(digits.substring(5, 7)) : 0;
        return ZoneOffset.ofHoursMinutesSeconds(sign * hours, sign * minutes, sign * seconds);
    }

    private static String offset(ZoneOffset offset) {
        int total = offset.getTotalSeconds();
        String sign = total < 0 ? "-" : "+";
        int abs = Math.abs(total);
        String hhmm = String.format(Locale.ROOT, "%s%02d:%02d", sign, abs / 3600, (abs % 3600) / 60);
        return abs % 60 == 0 ? hhmm : hhmm + String.format(Locale.ROOT, ":%02d", abs % 60);
    }
}
