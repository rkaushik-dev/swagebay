package io.github.rkaushikdev.swagebay.util;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The quoted form a value takes inside an error message or a log line.
 *
 * <p>Text is quoted so a trailing space or an empty value shows, numbers are bare, decimals say
 * they are decimals ({@code Decimal('2.50')}), truth values are {@code True} and {@code False},
 * an absent value is {@code None}, lists are bracketed and maps braced. A domain value that
 * knows its own quoted form implements {@link Quoted}. The form is part of every refusal the
 * command line prints, and the office searches old reports for it, so it does not change.
 */
public final class Repr {

    /** A value with a quoted form of its own, such as {@code Length(9525)}. */
    public interface Quoted {
        /** The quoted form, as it appears in a message. */
        String repr();
    }

    private Repr() {}

    /** The quoted form of any value. */
    public static String of(Object value) {
        return switch (value) {
            case null -> "None";
            case Optional<?> optional -> optional.map(Repr::of).orElse("None");
            case String text -> quote(text);
            case Character character -> quote(character.toString());
            case Boolean truth -> truth ? "True" : "False";
            case BigDecimal decimal -> "Decimal('" + decimal + "')";
            case Double number -> Floats.repr(number);
            case Float number -> Floats.repr(number.doubleValue());
            case Number number -> number.toString();
            case Quoted quoted -> quoted.repr();
            case Map<?, ?> map ->
                map.entrySet().stream()
                        .map(entry -> of(entry.getKey()) + ": " + of(entry.getValue()))
                        .collect(Collectors.joining(", ", "{", "}"));
            case Collection<?> items -> list(items.iterator());
            case Enum<?> member -> quote(member.toString());
            default -> value.toString();
        };
    }

    /**
     * Text in single quotes, or double quotes when it holds a single quote and no double one.
     * Backslashes and control characters are escaped.
     */
    public static String quote(String text) {
        char mark = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(mark);
        text.codePoints().forEach(point -> {
            switch (point) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (point == mark) {
                        out.append('\\').append((char) point);
                    } else if (point < 0x20 || point == 0x7f) {
                        out.append(String.format(java.util.Locale.ROOT, "\\x%02x", point));
                    } else {
                        out.appendCodePoint(point);
                    }
                }
            }
        });
        return out.append(mark).toString();
    }

    private static String list(Iterator<?> items) {
        StringBuilder out = new StringBuilder("[");
        while (items.hasNext()) {
            out.append(of(items.next()));
            if (items.hasNext()) {
                out.append(", ");
            }
        }
        return out.append(']').toString();
    }
}
