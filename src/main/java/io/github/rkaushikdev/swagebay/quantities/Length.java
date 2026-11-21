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
 * A length to the micrometre. Immutable, orderable, exact.
 *
 * <p>The trade measures in two systems at once and refuses to pick one. A wall is 0.375 inch on
 * the drawing and 9.53 mm on the mill certificate; a source to film distance is 24 inches or 610
 * mm depending on who wrote the procedure. Floating point millimetres lose the round inch values
 * and floating point inches lose the round millimetre ones, so neither is the store.
 *
 * <p>Micrometres are. One thousandth of an inch is 25.4 um exactly and no standard quotes anything
 * finer than 0.01 mm, so every value the trade quotes is a whole number of micrometres. Arithmetic
 * stays exact, equality means equality, and a sorted list of wire diameters sorts the way the
 * gauge card does. Scaling rounds to the nearest micrometre, ties away from zero.
 *
 * @param um the length in whole micrometres
 */
public record Length(long um) implements Comparable<Length>, Repr.Quoted {

    /** No length at all. */
    public static final Length ZERO = new Length(0);

    private static final BigDecimal UM_PER_MM = BigDecimal.valueOf(1000);
    private static final BigDecimal UM_PER_CM = BigDecimal.valueOf(10_000);
    private static final BigDecimal UM_PER_M = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal UM_PER_INCH = BigDecimal.valueOf(25_400);
    private static final BigDecimal UM_PER_THOU = new BigDecimal("25.4");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static final Pattern PATTERN = Pattern.compile(
            "\\s*(?<value>[+-]?\\d+(?:\\.\\d+)?)\\s*(?<unit>um|mm|cm|m|in|\"|thou|mil)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    /** A length from millimetres, to the nearest micrometre. */
    public static Length fromMm(BigDecimal mm) {
        return new Length(roundHalfUp(Decimals.multiply(mm, UM_PER_MM)));
    }

    /** A length from millimetres written as text. */
    public static Length fromMm(String mm) {
        return fromMm(Decimals.of(mm));
    }

    /** A length from whole millimetres. */
    public static Length fromMm(long mm) {
        return fromMm(BigDecimal.valueOf(mm));
    }

    /** A length from centimetres, to the nearest micrometre. */
    public static Length fromCm(BigDecimal cm) {
        return new Length(roundHalfUp(Decimals.multiply(cm, UM_PER_CM)));
    }

    /** A length from centimetres written as text. */
    public static Length fromCm(String cm) {
        return fromCm(Decimals.of(cm));
    }

    /** A length from whole centimetres. */
    public static Length fromCm(long cm) {
        return fromCm(BigDecimal.valueOf(cm));
    }

    /** A length from metres, to the nearest micrometre. */
    public static Length fromM(BigDecimal m) {
        return new Length(roundHalfUp(Decimals.multiply(m, UM_PER_M)));
    }

    /** A length from metres written as text. */
    public static Length fromM(String m) {
        return fromM(Decimals.of(m));
    }

    /** A length from whole metres. */
    public static Length fromM(long m) {
        return fromM(BigDecimal.valueOf(m));
    }

    /** A length from inches, to the nearest micrometre. */
    public static Length fromInch(BigDecimal inch) {
        return new Length(roundHalfUp(Decimals.multiply(inch, UM_PER_INCH)));
    }

    /** A length from inches written as text. */
    public static Length fromInch(String inch) {
        return fromInch(Decimals.of(inch));
    }

    /** A length from whole inches. */
    public static Length fromInch(long inch) {
        return fromInch(BigDecimal.valueOf(inch));
    }

    /** A length from thousandths of an inch, which the film crew still calls mils. */
    public static Length fromThou(BigDecimal thou) {
        return new Length(roundHalfUp(Decimals.multiply(thou, UM_PER_THOU)));
    }

    /** A length from thousandths of an inch written as text. */
    public static Length fromThou(String thou) {
        return fromThou(Decimals.of(thou));
    }

    /** A length from whole thousandths of an inch. */
    public static Length fromThou(long thou) {
        return fromThou(BigDecimal.valueOf(thou));
    }

    /**
     * Read a length off a procedure line or a spreadsheet cell: a number and one of {@code um},
     * {@code mm}, {@code cm}, {@code m}, {@code in}, {@code "}, {@code thou} or {@code mil}, in any
     * case and with any spacing.
     */
    public static Length parse(String text) {
        Matcher match = PATTERN.matcher(text);
        if (!match.matches()) {
            throw new ValidationError("not a length", Context.of("text", text));
        }
        BigDecimal value = new BigDecimal(match.group("value"));
        return switch (match.group("unit").toLowerCase(Locale.ROOT)) {
            case "um" -> new Length(roundHalfUp(value));
            case "mm" -> fromMm(value);
            case "cm" -> fromCm(value);
            case "m" -> fromM(value);
            case "in", "\"" -> fromInch(value);
            default -> fromThou(value);
        };
    }

    /** The length in millimetres, exactly. */
    public BigDecimal mm() {
        return Decimals.divide(BigDecimal.valueOf(um), UM_PER_MM);
    }

    /** The length in centimetres, exactly. */
    public BigDecimal cm() {
        return Decimals.divide(BigDecimal.valueOf(um), UM_PER_CM);
    }

    /** The length in metres, exactly. */
    public BigDecimal m() {
        return Decimals.divide(BigDecimal.valueOf(um), UM_PER_M);
    }

    /** The length in inches. */
    public BigDecimal inch() {
        return Decimals.divide(BigDecimal.valueOf(um), UM_PER_INCH);
    }

    /** The length in thousandths of an inch. */
    public BigDecimal thou() {
        return Decimals.divide(BigDecimal.valueOf(um), UM_PER_THOU);
    }

    /** This length and that one end to end. */
    public Length plus(Length other) {
        return new Length(Math.addExact(um, other.um));
    }

    /** This length less that one, which may go below zero. */
    public Length minus(Length other) {
        return new Length(Math.subtractExact(um, other.um));
    }

    /** Scaled by a factor, to the nearest micrometre, ties away from zero. */
    public Length times(BigDecimal factor) {
        return new Length(roundHalfUp(Decimals.multiply(BigDecimal.valueOf(um), factor)));
    }

    /** Scaled by a whole factor. */
    public Length times(long factor) {
        return times(BigDecimal.valueOf(factor));
    }

    /** Scaled by a factor given as a double, read as the decimal its text spells. */
    public Length times(double factor) {
        return times(Floats.decimal(factor));
    }

    /**
     * Divided by a number, to the nearest micrometre, ties away from zero.
     *
     * @throws ValidationError when the divisor is zero
     */
    public Length dividedBy(BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new ValidationError("cannot divide a length by zero");
        }
        return new Length(roundHalfUp(Decimals.divide(BigDecimal.valueOf(um), divisor)));
    }

    /** Divided by a whole number. */
    public Length dividedBy(long divisor) {
        return dividedBy(BigDecimal.valueOf(divisor));
    }

    /** The same length the other way. */
    public Length negate() {
        return new Length(Math.negateExact(um));
    }

    /** The length without its sign. */
    public Length abs() {
        return new Length(Math.absExact(um));
    }

    /**
     * This length over that one, dimensionless. Magnification and unsharpness both want this.
     *
     * @throws ValidationError against a zero length
     */
    public BigDecimal ratio(Length other) {
        if (other.um == 0) {
            throw new ValidationError("cannot take a ratio against a zero length");
        }
        return Decimals.divide(BigDecimal.valueOf(um), BigDecimal.valueOf(other.um));
    }

    /** Millimetres to that many places, ties away from zero: {@code 9.53 mm}. */
    public String formatMm(int places) {
        return Decimals.halfUp(mm(), places) + " mm";
    }

    /** Millimetres to two places. */
    public String formatMm() {
        return formatMm(2);
    }

    /** Inches to that many places, ties away from zero: {@code 0.375 in}. */
    public String formatInch(int places) {
        return Decimals.halfUp(inch(), places) + " in";
    }

    /** Inches to three places. */
    public String formatInch() {
        return formatInch(3);
    }

    /** Metres to that many places, ties away from zero: {@code 0.6 m}. */
    public String formatM(int places) {
        return Decimals.halfUp(m(), places) + " m";
    }

    /** Metres to one place. */
    public String formatM() {
        return formatM(1);
    }

    @Override
    public int compareTo(Length other) {
        return Long.compare(um, other.um);
    }

    /** Millimetres to two places, which is what a technique sheet shows. */
    @Override
    public String toString() {
        return formatMm();
    }

    @Override
    public String repr() {
        return "Length(" + um + ")";
    }

    /** To the nearest whole micrometre, a tie going away from zero. */
    private static long roundHalfUp(BigDecimal value) {
        if (value.signum() >= 0) {
            return Decimals.add(value, HALF).setScale(0, RoundingMode.FLOOR).longValueExact();
        }
        return -Decimals.add(value.negate(), HALF)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
