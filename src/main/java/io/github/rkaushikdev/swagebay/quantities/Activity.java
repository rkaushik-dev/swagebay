package io.github.rkaushikdev.swagebay.quantities;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import io.github.rkaushikdev.swagebay.util.Repr;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amount of radioactivity, to the becquerel.
 *
 * <p>Nobody in the field says becquerel. A projector holds ninety curies of iridium and the crew
 * talks in curies, but the licence, the ship papers and the leak test certificate are all in
 * becquerels because the regulator writes them that way. One curie is 37 000 000 000 becquerels by
 * definition, so whole becquerels hold every curie value the trade quotes and the conversion is
 * reversible. Activity is not a length and it is not a dose, and the type refuses to mix with
 * either.
 *
 * @param bq the activity in whole becquerels, never negative
 */
public record Activity(long bq) implements Comparable<Activity>, Repr.Quoted {

    /** Becquerels in one curie, by definition. */
    public static final long BQ_PER_CURIE = 37_000_000_000L;

    /** No activity at all. */
    public static final Activity ZERO = new Activity(0);

    private static final BigDecimal CURIE = BigDecimal.valueOf(BQ_PER_CURIE);
    private static final BigDecimal GIGA = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal TERA = BigDecimal.valueOf(1_000_000_000_000L);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static final Pattern PATTERN = Pattern.compile(
            "\\s*(?<value>[+-]?\\d+(?:\\.\\d+)?)\\s*(?<unit>bq|kbq|mbq|gbq|tbq|ci|mci|uci)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, BigDecimal> MULTIPLIERS = Map.of(
            "bq",
            BigDecimal.ONE,
            "kbq",
            BigDecimal.valueOf(1_000),
            "mbq",
            BigDecimal.valueOf(1_000_000),
            "gbq",
            GIGA,
            "tbq",
            TERA,
            "ci",
            CURIE,
            "mci",
            Decimals.divide(CURIE, 1000),
            "uci",
            Decimals.divide(CURIE, 1_000_000));

    /**
     * An activity of that many becquerels.
     *
     * @throws ValidationError when it is negative
     */
    public Activity {
        if (bq < 0) {
            throw new ValidationError("an activity cannot be negative", Context.of("given", bq));
        }
    }

    /** An activity from curies, to the nearest becquerel. */
    public static Activity fromCurie(BigDecimal curie) {
        return new Activity(roundHalfUp(Decimals.multiply(curie, CURIE)));
    }

    /** An activity from curies written as text. */
    public static Activity fromCurie(String curie) {
        return fromCurie(Decimals.of(curie));
    }

    /** An activity from whole curies. */
    public static Activity fromCurie(long curie) {
        return fromCurie(BigDecimal.valueOf(curie));
    }

    /** An activity from millicuries, to the nearest becquerel. */
    public static Activity fromMillicurie(BigDecimal millicurie) {
        return new Activity(roundHalfUp(Decimals.divide(Decimals.multiply(millicurie, CURIE), 1000)));
    }

    /** An activity from millicuries written as text. */
    public static Activity fromMillicurie(String millicurie) {
        return fromMillicurie(Decimals.of(millicurie));
    }

    /** An activity from whole millicuries. */
    public static Activity fromMillicurie(long millicurie) {
        return fromMillicurie(BigDecimal.valueOf(millicurie));
    }

    /** An activity from gigabecquerels, to the nearest becquerel. */
    public static Activity fromGbq(BigDecimal gbq) {
        return new Activity(roundHalfUp(Decimals.multiply(gbq, GIGA)));
    }

    /** An activity from gigabecquerels written as text. */
    public static Activity fromGbq(String gbq) {
        return fromGbq(Decimals.of(gbq));
    }

    /** An activity from whole gigabecquerels. */
    public static Activity fromGbq(long gbq) {
        return fromGbq(BigDecimal.valueOf(gbq));
    }

    /** An activity from terabecquerels, to the nearest becquerel. */
    public static Activity fromTbq(BigDecimal tbq) {
        return new Activity(roundHalfUp(Decimals.multiply(tbq, TERA)));
    }

    /** An activity from terabecquerels written as text. */
    public static Activity fromTbq(String tbq) {
        return fromTbq(Decimals.of(tbq));
    }

    /** An activity from whole terabecquerels. */
    public static Activity fromTbq(long tbq) {
        return fromTbq(BigDecimal.valueOf(tbq));
    }

    /**
     * Read an activity off a licence or a crew's note: a number and one of {@code Bq}, {@code kBq},
     * {@code MBq}, {@code GBq}, {@code TBq}, {@code Ci}, {@code mCi} or {@code uCi}, in any case.
     */
    public static Activity parse(String text) {
        Matcher match = PATTERN.matcher(text);
        if (!match.matches()) {
            throw new ValidationError("not an activity", Context.of("text", text));
        }
        BigDecimal multiplier = MULTIPLIERS.get(match.group("unit").toLowerCase(Locale.ROOT));
        return new Activity(roundHalfUp(Decimals.multiply(new BigDecimal(match.group("value")), multiplier)));
    }

    /** The activity in curies. */
    public BigDecimal curie() {
        return Decimals.divide(BigDecimal.valueOf(bq), CURIE);
    }

    /** The activity in gigabecquerels. */
    public BigDecimal gbq() {
        return Decimals.divide(BigDecimal.valueOf(bq), GIGA);
    }

    /** The activity in terabecquerels. */
    public BigDecimal tbq() {
        return Decimals.divide(BigDecimal.valueOf(bq), TERA);
    }

    /** Two sources' worth together. */
    public Activity plus(Activity other) {
        return new Activity(Math.addExact(bq, other.bq));
    }

    /**
     * This activity less that one.
     *
     * @throws ValidationError when that would go below zero
     */
    public Activity minus(Activity other) {
        if (other.bq > bq) {
            throw new ValidationError("an activity cannot go below zero", Context.of("left", bq, "right", other.bq));
        }
        return new Activity(bq - other.bq);
    }

    /**
     * Scaled by a factor, to the nearest becquerel.
     *
     * @throws ValidationError for a negative factor
     */
    public Activity times(BigDecimal factor) {
        if (factor.signum() < 0) {
            throw new ValidationError("cannot scale an activity by a negative factor", Context.of("factor", factor));
        }
        return new Activity(roundHalfUp(Decimals.multiply(BigDecimal.valueOf(bq), factor)));
    }

    /** Scaled by a whole factor. */
    public Activity times(long factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale an activity by a negative factor", Context.of("factor", factor));
        }
        return times(BigDecimal.valueOf(factor));
    }

    /** Scaled by a factor given as a double, read as the decimal its text spells. */
    public Activity times(double factor) {
        if (factor < 0) {
            throw new ValidationError("cannot scale an activity by a negative factor", Context.of("factor", factor));
        }
        return times(Floats.decimal(factor));
    }

    /**
     * This activity over that one, which is the decay fraction between them.
     *
     * @throws ValidationError against a spent source
     */
    public BigDecimal ratio(Activity other) {
        if (other.bq == 0) {
            throw new ValidationError("cannot take a ratio against a spent source");
        }
        return Decimals.divide(BigDecimal.valueOf(bq), BigDecimal.valueOf(other.bq));
    }

    /** Below the floor a source is no longer worth taking to a job. */
    public boolean isSpent(Activity floor) {
        return bq < floor.bq;
    }

    /** Curies to that many places, ties away from zero: {@code 90.0 Ci}. */
    public String formatCurie(int places) {
        return Decimals.halfUp(curie(), places) + " Ci";
    }

    /** Curies to one place. */
    public String formatCurie() {
        return formatCurie(1);
    }

    /** Terabecquerels to that many places, ties away from zero: {@code 3.33 TBq}. */
    public String formatTbq(int places) {
        return Decimals.halfUp(tbq(), places) + " TBq";
    }

    /** Terabecquerels to two places. */
    public String formatTbq() {
        return formatTbq(2);
    }

    @Override
    public int compareTo(Activity other) {
        return Long.compare(bq, other.bq);
    }

    /** Curies to one place, which is what the crew reads. */
    @Override
    public String toString() {
        return formatCurie();
    }

    @Override
    public String repr() {
        return "Activity(" + bq + ")";
    }

    /** The half rounded up: the floor of the value plus a half. */
    private static long roundHalfUp(BigDecimal value) {
        return Decimals.add(value, HALF).setScale(0, RoundingMode.FLOOR).longValueExact();
    }
}
