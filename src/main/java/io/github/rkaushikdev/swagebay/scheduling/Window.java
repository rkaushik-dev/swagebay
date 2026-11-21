package io.github.rkaushikdev.swagebay.scheduling;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * A span during which the area can be cleared and a shot can be made.
 *
 * <p>Gamma radiography is antisocial work: the area has to be cleared of everybody not on the crew,
 * which on a running plant means nights, weekends and shutdowns. A client grants windows, and the
 * windows are the binding constraint on the whole job.
 *
 * <p>A window is wall clock time on purpose, which is why both ends are {@link LocalDateTime}: site
 * permits are written in local wall clock time and a crew reading a permit that says 22:00 does not
 * want to think about offsets, so an instant cannot be handed in at all. Conversion happens at the
 * edges, where the caller knows what zone they meant. A window contains its start and not its end.
 * Windows order by start, then end, then kind.
 *
 * @param startsAt when the area can first be cleared
 * @param endsAt when it has to be handed back, after the start
 * @param kind why it is open
 */
public record Window(LocalDateTime startsAt, LocalDateTime endsAt, WindowKind kind) implements Comparable<Window> {

    /** When the usual night window opens. */
    public static final LocalTime NIGHT_OPENS = LocalTime.of(22, 0);

    /** When the usual night window closes, the next morning. */
    public static final LocalTime NIGHT_CLOSES = LocalTime.of(5, 30);

    private static final Comparator<Window> ORDER =
            Comparator.comparing(Window::startsAt).thenComparing(Window::endsAt).thenComparing(Window::kind);
    private static final DateTimeFormatter OPENS = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter CLOSES = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * A window.
     *
     * @throws ValidationError for a window that does not end after it starts
     */
    public Window {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(kind, "kind");
        if (!endsAt.isAfter(startsAt)) {
            throw new ValidationError(
                    "a window has to end after it starts",
                    Context.of("starts_at", Times.iso(startsAt), "ends_at", Times.iso(endsAt)));
        }
    }

    /** A night window. */
    public Window(LocalDateTime startsAt, LocalDateTime endsAt) {
        this(startsAt, endsAt, WindowKind.NIGHT);
    }

    /** The usual arrangement: opens at ten in the evening of that day and closes at half past five next morning. */
    public static Window night(LocalDate day) {
        return night(day, NIGHT_OPENS, NIGHT_CLOSES);
    }

    /** A night window opening on the evening of that day and closing the next morning. */
    public static Window night(LocalDate day, LocalTime opens, LocalTime closes) {
        return new Window(LocalDateTime.of(day, opens), LocalDateTime.of(day.plusDays(1), closes), WindowKind.NIGHT);
    }

    /** How long the window is open. */
    public Duration length() {
        return new Duration(millisBetween(startsAt, endsAt));
    }

    /** Whether the moment is inside the window: the start is, the end is not. */
    public boolean contains(LocalDateTime moment) {
        return !moment.isBefore(startsAt) && moment.isBefore(endsAt);
    }

    /** Whether the two windows share any time; two that only touch do not. */
    public boolean overlaps(Window other) {
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }

    /** The time left in the window from that moment, all of it before the window opens and none after it closes. */
    public Duration remainingAt(LocalDateTime moment) {
        if (!moment.isBefore(endsAt)) {
            return Duration.ZERO;
        }
        LocalDateTime start = moment.isAfter(startsAt) ? moment : startsAt;
        return new Duration(millisBetween(start, endsAt));
    }

    /** Whether a job that long, starting then, finishes before the window closes. */
    public boolean fits(Duration needed, LocalDateTime startingAt) {
        return remainingAt(startingAt).ms() >= needed.ms();
    }

    @Override
    public int compareTo(Window other) {
        return ORDER.compare(this, other);
    }

    /** The window the way a permit reads: {@code permit 2024-04-08 22:00 to 05:30}. */
    @Override
    public String toString() {
        return kind + " " + startsAt.format(OPENS) + " to " + endsAt.format(CLOSES);
    }

    /**
     * The whole milliseconds between two moments, the fraction dropped: the microseconds read as
     * seconds, then as milliseconds, each step in binary floating point.
     */
    static long millisBetween(LocalDateTime from, LocalDateTime to) {
        double seconds = ChronoUnit.MICROS.between(from, to) / 1_000_000.0;
        return (long) (seconds * 1000);
    }
}
