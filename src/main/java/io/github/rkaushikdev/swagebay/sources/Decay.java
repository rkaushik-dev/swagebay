package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Times;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Decaying a source to the day of the shot.
 *
 * <p>A source is bought with a certificate that says how many curies it held at an assay moment,
 * and it has been getting weaker ever since. Every exposure time is wrong unless the activity used
 * is the activity on the morning of the shot. Iridium loses a third of itself in five weeks, and a
 * crew that uses last month's number underexposes by that much and reshoots the weld.
 *
 * <p>The arithmetic is the ordinary exponential, done in decimal at {@link Decimals#CONTEXT}
 * rather than in binary floating point. The reason is reproducibility: the same certificate and the
 * same date give the same seconds today and in four years when somebody audits the film.
 */
public final class Decay {

    private static final BigDecimal LN_2 = Decimals.ln(BigDecimal.valueOf(2));
    private static final BigDecimal MS_PER_HOUR = BigDecimal.valueOf(Duration.MS_PER_HOUR);

    private Decay() {}

    /**
     * What fraction of the activity is left after that long.
     *
     * @throws ValidationError for an isotope without a positive half life
     */
    public static BigDecimal decayFraction(Isotope isotope, Duration elapsed) {
        Duration halfLife = isotope.halfLife();
        if (halfLife.ms() <= 0) {
            throw new ValidationError("an isotope needs a positive half life", Context.of("isotope", isotope.key()));
        }
        BigDecimal periods = Decimals.divide(BigDecimal.valueOf(elapsed.ms()), BigDecimal.valueOf(halfLife.ms()));
        return Decimals.exp(Decimals.multiply(periods.negate(), LN_2));
    }

    /** The activity left after that long, to the nearest becquerel. */
    public static Activity decay(Activity activity, Isotope isotope, Duration elapsed) {
        return activity.times(decayFraction(isotope, elapsed));
    }

    /**
     * The activity at the moment of the shot, from the certificate.
     *
     * <p>Both moments are wall clock time, so an instant cannot be compared with one by mistake.
     *
     * @throws ValidationError for a moment before the assay
     */
    public static Activity activityOn(
            Activity assayActivity, LocalDateTime assayAt, Isotope isotope, LocalDateTime at) {
        if (at.isBefore(assayAt)) {
            throw new ValidationError(
                    "cannot decay a source to a date before its assay",
                    Context.of("assay_at", Times.iso(assayAt), "at", Times.iso(at)));
        }
        return decay(assayActivity, isotope, span(assayAt, at));
    }

    /**
     * How long it takes to fall to that fraction of where it started, to the millisecond below.
     *
     * @throws ValidationError for a fraction outside (0, 1]
     */
    public static Duration elapsedForFraction(Isotope isotope, BigDecimal fraction) {
        if (fraction.signum() <= 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError("a decay fraction sits in (0, 1]", Context.of("fraction", fraction.toString()));
        }
        BigDecimal periods = Decimals.divide(Decimals.ln(fraction).negate(), LN_2);
        return new Duration(Decimals.truncate(
                Decimals.multiply(periods, BigDecimal.valueOf(isotope.halfLife().ms()))));
    }

    /**
     * How long from that activity down to this one.
     *
     * @throws ValidationError from a spent source, towards a stronger one, or towards nothing
     */
    public static Duration timeToReach(Isotope isotope, Activity start, Activity target) {
        if (start.bq() == 0) {
            throw new ValidationError("a spent source does not decay to anything");
        }
        if (target.bq() > start.bq()) {
            throw new ValidationError(
                    "a source does not grow", Context.of("start_bq", start.bq(), "target_bq", target.bq()));
        }
        if (target.bq() == 0) {
            throw new ValidationError("a source never reaches zero");
        }
        return elapsedForFraction(isotope, target.ratio(start));
    }

    /** The moment the source falls to that activity. */
    public static LocalDateTime dateReaching(Isotope isotope, Activity start, Activity target, LocalDateTime at) {
        return at.plus(
                java.time.Duration.ofMillis(timeToReach(isotope, start, target).ms()));
    }

    /** The span in half lives, which is how a source history sheet quotes it. */
    public static BigDecimal halfLivesElapsed(Isotope isotope, Duration elapsed) {
        return Decimals.divide(
                BigDecimal.valueOf(elapsed.ms()),
                BigDecimal.valueOf(isotope.halfLife().ms()));
    }

    /** That many hours, to the millisecond below, so a caller need not reach for milliseconds. */
    public static Duration hours(BigDecimal value) {
        return new Duration(Decimals.truncate(Decimals.multiply(value, MS_PER_HOUR)));
    }

    /** That many whole hours. */
    public static Duration hours(long value) {
        return hours(BigDecimal.valueOf(value));
    }

    /**
     * The span between two moments, to the millisecond below: the microseconds read as seconds, then
     * as milliseconds, each step in binary floating point.
     */
    private static Duration span(LocalDateTime from, LocalDateTime to) {
        double totalSeconds = ChronoUnit.MICROS.between(from, to) / 1_000_000.0;
        long totalMs = (long) (totalSeconds * 1000);
        if (totalMs < 0) {
            throw new ValidationError("a decay span cannot be negative", Context.of("ms", totalMs));
        }
        return new Duration(totalMs);
    }
}
