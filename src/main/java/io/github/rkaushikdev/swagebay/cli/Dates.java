package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Days and moments typed on the command line.
 *
 * <p>Each option names the formats it accepts, tried in order, the first that reads the whole of
 * the text winning. A format reads a field the way a person types it: the year in four digits, the
 * month, day, hour, minute and second in one digit or two, and a day may be padded with a space.
 * Whatever the format, the value is a moment, a bare day being its midnight.
 */
final class Dates {

    /** A day, {@code %Y-%m-%d}. */
    static final String DAY = "%Y-%m-%d";

    /** A moment with a {@code T} before the time. */
    static final String ISO_MOMENT = "%Y-%m-%dT%H:%M:%S";

    /** A moment with a space before the time. */
    static final String SPACED_MOMENT = "%Y-%m-%d %H:%M:%S";

    /** What a help page shows for a day option. */
    static final String DAY_LABEL = "[" + DAY + "]";

    /** What a help page shows for a moment option, naming each format it reads. */
    static final String MOMENT_LABEL = "[" + ISO_MOMENT + "|" + SPACED_MOMENT + "|" + DAY + "]";

    private static final String DATE = "(\\d\\d\\d\\d)-(1[0-2]|0[1-9]|[1-9])-(3[01]|[12]\\d|0[1-9]|[1-9]| [1-9])";
    private static final String TIME = "(2[0-3]|[0-1]\\d|\\d):([0-5]\\d|\\d):(6[0-1]|[0-5]\\d|\\d)";
    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private Dates() {}

    /** A format by its name and the pattern that reads it. */
    private record Format(String name, Pattern pattern) {

        static Format of(String name, String pattern) {
            return new Format(name, Pattern.compile(pattern, FLAGS));
        }
    }

    private static final Format DAY_FORMAT = Format.of(DAY, DATE);
    private static final Format ISO_FORMAT = Format.of(ISO_MOMENT, DATE + "T" + TIME);
    private static final Format SPACED_FORMAT = Format.of(SPACED_MOMENT, DATE + "\\s+" + TIME);

    /** The moment {@code text} is in the first of {@code formats} that reads all of it. */
    private static LocalDateTime parse(String text, List<Format> formats) {
        for (Format format : formats) {
            Matcher match = format.pattern().matcher(text);
            if (match.lookingAt() && match.end() == text.length()) {
                Optional<LocalDateTime> moment = moment(match);
                if (moment.isPresent()) {
                    return moment.get();
                }
            }
        }
        String names = formats.stream().map(format -> Repr.quote(format.name())).collect(Collectors.joining(", "));
        String wanted = formats.size() == 1 ? "the format " + names : "the formats " + names;
        throw new TypeConversionException(Repr.quote(text) + " does not match " + wanted + ".");
    }

    /** The moment the fields read, unless they name one the calendar does not have. */
    private static Optional<LocalDateTime> moment(Matcher match) {
        boolean timed = match.groupCount() > 3;
        try {
            return Optional.of(LocalDateTime.of(
                    field(match, 1),
                    field(match, 2),
                    field(match, 3),
                    timed ? field(match, 4) : 0,
                    timed ? field(match, 5) : 0,
                    timed ? field(match, 6) : 0));
        } catch (DateTimeException impossible) {
            return Optional.empty();
        }
    }

    private static int field(Matcher match, int group) {
        return Integer.parseInt(match.group(group).strip());
    }

    /** {@code --on} and its kind: a day, {@value #DAY}. */
    static final class Day implements ITypeConverter<LocalDateTime> {

        @Override
        public LocalDateTime convert(String text) {
            return parse(text, List.of(DAY_FORMAT));
        }
    }

    /** {@code --at} and its kind: a moment with a {@code T} or a space before the time, or a day. */
    static final class Timestamp implements ITypeConverter<LocalDateTime> {

        @Override
        public LocalDateTime convert(String text) {
            return parse(text, List.of(ISO_FORMAT, SPACED_FORMAT, DAY_FORMAT));
        }
    }
}
