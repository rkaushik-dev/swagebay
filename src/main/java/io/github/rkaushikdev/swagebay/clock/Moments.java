package io.github.rkaushikdev.swagebay.clock;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

/**
 * Timestamps, and the one rule kept about them.
 *
 * <p>Three kinds of moment turn up and they are genuinely different. A source certificate carries
 * an <em>instant</em>, a physical fact about when the activity was measured, which belongs in
 * universal time. A site permit carries <em>wall clock time</em>: clear from twenty two hundred
 * means whatever the clock on the wall of the site office says, and converting that to universal
 * time and back is how a window moves an hour twice a year. A calendar <em>date</em> carries
 * neither.
 *
 * <p>An instant is an {@link OffsetDateTime}, wall clock time a {@link LocalDateTime}, and a day a
 * {@link LocalDate}. The records keep every moment as wall clock time, so the domain types take
 * {@code LocalDateTime} and the compiler keeps the kinds apart there. These guards are for the
 * places a moment arrives whose kind is only known when it is read, and what they will not do is
 * compare across the kinds: that is a refusal rather than a wrong answer.
 */
public final class Moments {

    private Moments() {}

    /** Whether the moment knows where on earth it happened. */
    public static boolean isAware(Temporal moment) {
        return moment.isSupported(ChronoField.OFFSET_SECONDS);
    }

    /**
     * The moment itself, if it is an instant.
     *
     * @param what what the moment is, for the refusal
     * @throws ValidationError for wall clock time
     */
    public static <T extends Temporal> T requireAware(T moment, String what) {
        if (!isAware(moment)) {
            throw new ValidationError("a " + what + " has to carry a time zone", Context.of("given", iso(moment)));
        }
        return moment;
    }

    /** {@link #requireAware(Temporal, String)} for a plain timestamp. */
    public static <T extends Temporal> T requireAware(T moment) {
        return requireAware(moment, "timestamp");
    }

    /**
     * The moment itself, if it is wall clock time, because the wall does not know its zone either.
     *
     * @param what what the moment is, for the refusal
     * @throws ValidationError for an instant
     */
    public static <T extends Temporal> T requireNaive(T moment, String what) {
        if (isAware(moment)) {
            throw new ValidationError(
                    "a " + what + " is read off a clock and carries no zone", Context.of("given", iso(moment)));
        }
        return moment;
    }

    /** {@link #requireNaive(Temporal, String)} for plain wall clock time. */
    public static <T extends Temporal> T requireNaive(T moment) {
        return requireNaive(moment, "wall clock time");
    }

    /**
     * Refuse a comparison between an instant and a wall clock time.
     *
     * @throws ValidationError when the two are of different kinds
     */
    public static void sameKind(Temporal left, Temporal right) {
        if (isAware(left) != isAware(right)) {
            throw new ValidationError(
                    "cannot compare an instant with a wall clock time",
                    Context.of("left_aware", isAware(left), "right_aware", isAware(right)));
        }
    }

    /** The instant read in universal time, refusing a moment that is not an instant. */
    public static OffsetDateTime toUtc(Temporal moment) {
        return OffsetDateTime.from(requireAware(moment)).withOffsetSameInstant(ZoneOffset.UTC);
    }

    /**
     * The instant read at a stated offset, which is what a site report quotes.
     *
     * @throws ValidationError for an offset beyond fourteen hours either way
     */
    public static OffsetDateTime atOffset(Temporal moment, int hours) {
        if (hours < -14 || hours > 14) {
            throw new ValidationError("that is not an offset any place uses", Context.of("hours", hours));
        }
        return OffsetDateTime.from(requireAware(moment)).withOffsetSameInstant(ZoneOffset.ofHours(hours));
    }

    /**
     * The time between two moments of the same kind.
     *
     * @throws ValidationError across kinds, or when the later one is earlier
     */
    public static Duration span(Temporal earlier, Temporal later) {
        sameKind(earlier, later);
        Duration between = Duration.between(earlier, later);
        if (between.isNegative()) {
            throw new ValidationError("a span runs forwards", Context.of("earlier", iso(earlier), "later", iso(later)));
        }
        return between;
    }

    /** The calendar day a moment falls on where it was read. */
    public static LocalDate onDay(Temporal moment) {
        return LocalDate.from(moment);
    }

    /**
     * The calendar day an instant falls on in universal time.
     *
     * <p>This is not the local day and the difference is real: a shot made at ten past midnight
     * local on the fifth was made on the fourth in universal time.
     */
    public static LocalDate utcDay(Temporal moment) {
        return toUtc(moment).toLocalDate();
    }

    /** Midnight at the start of a day, as wall clock time. */
    public static LocalDateTime startOf(LocalDate day) {
        return day.atStartOfDay();
    }

    /**
     * Midnight on a day, of the same kind as a moment already in hand: an instant at the same
     * offset, or wall clock time, so the two can always be compared.
     */
    public static Temporal midnightLike(LocalDate day, Temporal other) {
        LocalDateTime at = startOf(day);
        if (isAware(other)) {
            return at.atOffset(ZoneOffset.ofTotalSeconds(other.get(ChronoField.OFFSET_SECONDS)));
        }
        return at;
    }

    /** The last microsecond of a day, as wall clock time. */
    public static LocalDateTime endOf(LocalDate day) {
        return LocalDateTime.of(day, LocalTime.MAX.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Whether a wall clock moment falls on a day.
     *
     * @throws ValidationError for an instant, which is on no wall clock day until it is read somewhere
     */
    public static boolean within(Temporal moment, LocalDate day) {
        LocalDateTime local = LocalDateTime.from(requireNaive(moment));
        return !local.isBefore(startOf(day)) && !local.isAfter(endOf(day));
    }

    /** The ISO text of either kind of moment. */
    public static String iso(Temporal moment) {
        if (isAware(moment)) {
            return Times.iso(OffsetDateTime.from(moment));
        }
        return Times.iso(LocalDateTime.from(moment));
    }
}
