package io.github.rkaushikdev.swagebay.quantities;

import java.math.BigDecimal;

/**
 * The short constructors for the quantities, for {@code import static}: {@code mm(610)},
 * {@code inch("0.375")}, {@code curie(90)}, {@code minutes("1.5")}, {@code usvPerHour(20)}.
 *
 * <p>Each takes a whole number, a decimal, or the decimal written as text, and rounds to the
 * store's resolution the way the quantity's own {@code from} method does.
 */
public final class Units {

    private Units() {}

    /** Millimetres. */
    public static Length mm(long value) {
        return Length.fromMm(value);
    }

    /** Millimetres written as text. */
    public static Length mm(String value) {
        return Length.fromMm(value);
    }

    /** Millimetres. */
    public static Length mm(BigDecimal value) {
        return Length.fromMm(value);
    }

    /** Inches. */
    public static Length inch(long value) {
        return Length.fromInch(value);
    }

    /** Inches written as text. */
    public static Length inch(String value) {
        return Length.fromInch(value);
    }

    /** Inches. */
    public static Length inch(BigDecimal value) {
        return Length.fromInch(value);
    }

    /** Thousandths of an inch. */
    public static Length thou(long value) {
        return Length.fromThou(value);
    }

    /** Thousandths of an inch written as text. */
    public static Length thou(String value) {
        return Length.fromThou(value);
    }

    /** Thousandths of an inch. */
    public static Length thou(BigDecimal value) {
        return Length.fromThou(value);
    }

    /** Curies. */
    public static Activity curie(long value) {
        return Activity.fromCurie(value);
    }

    /** Curies written as text. */
    public static Activity curie(String value) {
        return Activity.fromCurie(value);
    }

    /** Curies. */
    public static Activity curie(BigDecimal value) {
        return Activity.fromCurie(value);
    }

    /** Gigabecquerels. */
    public static Activity gbq(long value) {
        return Activity.fromGbq(value);
    }

    /** Gigabecquerels written as text. */
    public static Activity gbq(String value) {
        return Activity.fromGbq(value);
    }

    /** Gigabecquerels. */
    public static Activity gbq(BigDecimal value) {
        return Activity.fromGbq(value);
    }

    /** Seconds. */
    public static Duration seconds(long value) {
        return Duration.fromSeconds(value);
    }

    /** Seconds written as text. */
    public static Duration seconds(String value) {
        return Duration.fromSeconds(value);
    }

    /** Seconds. */
    public static Duration seconds(BigDecimal value) {
        return Duration.fromSeconds(value);
    }

    /** Minutes. */
    public static Duration minutes(long value) {
        return Duration.fromMinutes(value);
    }

    /** Minutes written as text. */
    public static Duration minutes(String value) {
        return Duration.fromMinutes(value);
    }

    /** Minutes. */
    public static Duration minutes(BigDecimal value) {
        return Duration.fromMinutes(value);
    }

    /** Microsieverts. */
    public static Dose usv(long value) {
        return Dose.fromUsv(value);
    }

    /** Microsieverts written as text. */
    public static Dose usv(String value) {
        return Dose.fromUsv(value);
    }

    /** Microsieverts. */
    public static Dose usv(BigDecimal value) {
        return Dose.fromUsv(value);
    }

    /** Microsieverts an hour. */
    public static DoseRate usvPerHour(long value) {
        return DoseRate.fromUsvPerHour(value);
    }

    /** Microsieverts an hour written as text. */
    public static DoseRate usvPerHour(String value) {
        return DoseRate.fromUsvPerHour(value);
    }

    /** Microsieverts an hour. */
    public static DoseRate usvPerHour(BigDecimal value) {
        return DoseRate.fromUsvPerHour(value);
    }

    /** Milliroentgen an hour. */
    public static DoseRate mrPerHour(long value) {
        return DoseRate.fromMrPerHour(value);
    }

    /** Milliroentgen an hour written as text. */
    public static DoseRate mrPerHour(String value) {
        return DoseRate.fromMrPerHour(value);
    }

    /** Milliroentgen an hour. */
    public static DoseRate mrPerHour(BigDecimal value) {
        return DoseRate.fromMrPerHour(value);
    }
}
