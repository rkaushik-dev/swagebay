package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.util.Repr;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigInteger;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Whole numbers typed on the command line, and the ones with a floor.
 *
 * <p>A count is read with white space round it allowed, a sign, and underscores between digits
 * ({@code 1_000}), which is how a number pasted out of a spreadsheet or a script tends to arrive.
 * Anything else, a number too large to be a count included, is a usage error that names the value.
 */
final class Counts {

    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d+(?:_\\d+)*", Pattern.UNICODE_CHARACTER_CLASS);

    private Counts() {}

    /** The whole number {@code text} is. */
    static int whole(String text) {
        String trimmed = Text.strip(text);
        if (WHOLE.matcher(trimmed).matches()) {
            BigInteger value = new BigInteger(trimmed.replace("_", ""));
            if (value.bitLength() < Integer.SIZE) {
                return value.intValue();
            }
        }
        throw new TypeConversionException(Repr.quote(text) + " is not a valid integer.");
    }

    private static int atLeast(String text, int floor) {
        int value = whole(text);
        if (value < floor) {
            throw new TypeConversionException(value + " is not in the range x>=" + floor + ".");
        }
        return value;
    }

    /** Any whole number. */
    static final class Whole implements ITypeConverter<Integer> {

        @Override
        public Integer convert(String text) {
            return whole(text);
        }
    }

    /** A whole number that is not negative. */
    static final class NotNegative implements ITypeConverter<Integer> {

        @Override
        public Integer convert(String text) {
            return atLeast(text, 0);
        }
    }

    /** A whole number of one or more. */
    static final class Positive implements ITypeConverter<Integer> {

        @Override
        public Integer convert(String text) {
            return atLeast(text, 1);
        }
    }
}
