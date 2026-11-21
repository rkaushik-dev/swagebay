package io.github.rkaushikdev.swagebay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Binary floating point values, written and rounded the way the reports print them.
 *
 * <p>The text of a double is its shortest round-tripping digits, in positional form from
 * {@code 0.0001} up to sixteen integer digits and as {@code 1e-05} or {@code 1e+16} outside that,
 * always with a point or an exponent so it never reads as a whole number. Rounding is to the
 * nearest, a tie going to the even neighbour.
 */
public final class Floats {

    private Floats() {}

    /** The shortest text that reads back as the same double. */
    public static String repr(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0.0" : "0.0";
        }
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int point = digits.length() - shortest.scale();
        String sign = value < 0 ? "-" : "";
        if (point > -4 && point <= 16) {
            if (point <= 0) {
                return sign + "0." + "0".repeat(-point) + digits;
            }
            if (point >= digits.length()) {
                return sign + digits + "0".repeat(point - digits.length()) + ".0";
            }
            return sign + digits.substring(0, point) + "." + digits.substring(point);
        }
        int exponent = point - 1;
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return String.format(Locale.ROOT, "%s%se%s%02d", sign, mantissa, exponent < 0 ? "-" : "+", Math.abs(exponent));
    }

    /** The decimal a double's text spells, exactly: {@code 0.1} is one tenth, not the binary value. */
    public static BigDecimal decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ArithmeticException("not a finite number: " + value);
        }
        return new BigDecimal(repr(value));
    }

    /** The nearest whole number, a tie going to the even one. */
    public static long round(double value) {
        return (long) Math.rint(value);
    }

    /**
     * Rounded to that many places, a tie going to the even digit, judged on the exact binary
     * value (so 2.675 to two places is 2.67, because the double is just below the half).
     */
    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Fixed to that many places, judged on the exact binary value, as a fixed-point format does. */
    public static String fixed(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return repr(value);
        }
        String text =
                new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
        boolean negativeZero = (value < 0 || 1 / value < 0) && !text.startsWith("-");
        return negativeZero ? "-" + text : text;
    }
}
