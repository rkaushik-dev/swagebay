package io.github.rkaushikdev.swagebay.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Decimal arithmetic at the precision every calculation in the package is done at.
 *
 * <p>Every intermediate result is held to 28 significant digits, a tie going to the even
 * digit, and every transcendental ({@link #ln}, {@link #exp}, {@link #sqrt}, {@link #power}) is
 * correctly rounded to that precision. An exact quotient keeps the scale of the dividend less
 * the scale of the divisor where it can, so ten thousand micrometres read in millimetres is
 * {@code 10} and not {@code 10.000} or {@code 1E+1}. Exposure charts, decay and the dose field
 * are all interpolated through these, and a precision that drifts by a digit moves an answer
 * that is later rounded up to a whole film.
 */
public final class Decimals {

    /** Twenty eight significant digits, a tie going to the even digit. */
    public static final MathContext CONTEXT = new MathContext(28, RoundingMode.HALF_EVEN);

    private static final int GUARD = 24;
    private static final MathContext WORK = new MathContext(CONTEXT.getPrecision() + GUARD, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private Decimals() {}

    /** A decimal from a whole number, exactly. */
    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value);
    }

    /** A decimal from its text, exactly, keeping the digits as written ({@code "2.50"} keeps the zero). */
    public static BigDecimal of(String text) {
        return new BigDecimal(text.strip());
    }

    /** The quotient at {@link #CONTEXT}, exact where the exact quotient fits. */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, CONTEXT);
    }

    /** {@link #divide(BigDecimal, BigDecimal)} with a whole divisor. */
    public static BigDecimal divide(BigDecimal dividend, long divisor) {
        return divide(dividend, BigDecimal.valueOf(divisor));
    }

    /** {@link #divide(BigDecimal, BigDecimal)} of two whole numbers. */
    public static BigDecimal divide(long dividend, long divisor) {
        return divide(BigDecimal.valueOf(dividend), BigDecimal.valueOf(divisor));
    }

    /** The product at {@link #CONTEXT}. */
    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return left.multiply(right, CONTEXT);
    }

    /** The sum at {@link #CONTEXT}. */
    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return left.add(right, CONTEXT);
    }

    /** The difference at {@link #CONTEXT}. */
    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return left.subtract(right, CONTEXT);
    }

    /** Fixed to that many places, ties away from zero, which is how a shop floor rounds. */
    public static BigDecimal halfUp(BigDecimal value, int places) {
        return value.setScale(places, RoundingMode.HALF_UP);
    }

    /** Fixed to that many places, a tie going to the even digit. */
    public static BigDecimal halfEven(BigDecimal value, int places) {
        return value.setScale(places, RoundingMode.HALF_EVEN);
    }

    /** The nearest whole number, ties away from zero. */
    public static long roundHalfUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The nearest whole number, a tie going to the even one. */
    public static long roundHalfEven(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }

    /** The whole part, the fraction dropped towards zero. */
    public static long truncate(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** The largest whole number not above the value. */
    public static long floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** The smallest whole number not below the value. */
    public static long ceiling(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /**
     * The value with its trailing zeros removed, rounded to {@link #CONTEXT} first. A hundred comes
     * out as {@code 1E+2}; {@link #plain(BigDecimal)} is the form for people.
     */
    public static BigDecimal normalize(BigDecimal value) {
        return value.round(CONTEXT).stripTrailingZeros();
    }

    /** The trailing zeros gone and no exponent: {@code 100} rather than {@code 1E+2}. */
    public static BigDecimal plain(BigDecimal value) {
        BigDecimal trimmed = normalize(value);
        return trimmed.scale() < 0 ? trimmed.setScale(0) : trimmed;
    }

    /** Whether the value is a whole number, whatever its scale. */
    public static boolean isWhole(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    /** The natural logarithm, correctly rounded to {@link #CONTEXT}. */
    public static BigDecimal ln(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("the logarithm is defined only above zero: " + value);
        }
        if (value.compareTo(BigDecimal.ONE) == 0) {
            return BigDecimal.ZERO;
        }
        return lnWork(value).round(CONTEXT);
    }

    /** The base ten logarithm, correctly rounded to {@link #CONTEXT}; exact for a power of ten. */
    public static BigDecimal log10(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("the logarithm is defined only above zero: " + value);
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.unscaledValue().equals(BigInteger.ONE)) {
            return BigDecimal.valueOf(-stripped.scale());
        }
        return lnWork(value).divide(lnWork(TEN), WORK).round(CONTEXT);
    }

    /** e to the power of the value, correctly rounded to {@link #CONTEXT}. */
    public static BigDecimal exp(BigDecimal value) {
        if (value.signum() == 0) {
            return BigDecimal.ONE;
        }
        return expWork(value, WORK).round(CONTEXT);
    }

    /**
     * The square root, correctly rounded to {@link #CONTEXT}. An exact root keeps half the scale of
     * the value where it can, so the root of {@code 1.00} is {@code 1.0} and of {@code 100} is
     * {@code 10}.
     */
    public static BigDecimal sqrt(BigDecimal value) {
        if (value.signum() < 0) {
            throw new ArithmeticException("no square root below zero: " + value);
        }
        int ideal = Math.ceilDiv(value.scale(), 2);
        if (value.signum() == 0) {
            return BigDecimal.ZERO.setScale(ideal);
        }
        BigDecimal root = value.sqrt(WORK);
        BigDecimal stripped = root.stripTrailingZeros();
        if (stripped.precision() <= CONTEXT.getPrecision()
                && stripped.multiply(stripped).compareTo(value) == 0) {
            if (stripped.scale() < ideal && stripped.precision() + ideal - stripped.scale() <= CONTEXT.getPrecision()) {
                return stripped.setScale(ideal);
            }
            return stripped;
        }
        return root.round(CONTEXT);
    }

    /**
     * The base raised to the exponent at {@link #CONTEXT}. A whole exponent multiplies exactly
     * before rounding; any other goes through the logarithm.
     */
    public static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        if (isWhole(exponent) && exponent.abs().compareTo(BigDecimal.valueOf(999_999)) <= 0) {
            int whole = exponent.intValueExact();
            if (whole >= 0) {
                return base.pow(whole, CONTEXT);
            }
            return BigDecimal.ONE.divide(base.pow(-whole, WORK), CONTEXT);
        }
        if (base.signum() <= 0) {
            throw new ArithmeticException("a fractional power needs a base above zero: " + base);
        }
        return expWork(lnWork(base).multiply(exponent, WORK), WORK).round(CONTEXT);
    }

    private static BigDecimal lnWork(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int exponent = stripped.precision() - stripped.scale() - 1;
        BigDecimal mantissa = stripped.movePointLeft(exponent);
        BigDecimal result = lnNear(mantissa);
        if (exponent != 0) {
            result = result.add(lnNear(TEN).multiply(BigDecimal.valueOf(exponent), WORK), WORK);
        }
        return result;
    }

    /** Halley iteration on the exponential from a double precision start; the argument is in [1, 10). */
    private static BigDecimal lnNear(BigDecimal value) {
        BigDecimal estimate = new BigDecimal(Math.log(value.doubleValue()));
        for (int round = 0; round < 4; round++) {
            BigDecimal e = expWork(estimate, WORK);
            BigDecimal step = TWO.multiply(value.subtract(e, WORK), WORK).divide(value.add(e, WORK), WORK);
            estimate = estimate.add(step, WORK);
        }
        return estimate;
    }

    private static BigDecimal expWork(BigDecimal value, MathContext context) {
        int halvings = 0;
        BigDecimal reduced = value;
        BigDecimal limit = new BigDecimal("0.001");
        while (reduced.abs().compareTo(limit) > 0) {
            reduced = reduced.multiply(HALF);
            halvings++;
        }
        MathContext inner = new MathContext(context.getPrecision() + halvings / 3 + 4, RoundingMode.HALF_EVEN);
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        BigDecimal threshold = BigDecimal.ONE.movePointLeft(inner.getPrecision() + 2);
        for (int n = 1; n < 1000; n++) {
            term = term.multiply(reduced, inner).divide(BigDecimal.valueOf(n), inner);
            sum = sum.add(term, inner);
            if (term.abs().compareTo(threshold) < 0) {
                break;
            }
        }
        for (int i = 0; i < halvings; i++) {
            sum = sum.multiply(sum, inner);
        }
        return sum.round(context);
    }
}
