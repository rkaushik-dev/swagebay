package io.github.rkaushikdev.swagebay.scheduling;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The windows a client has granted, kept in order and never overlapping. */
public final class SiteSchedule {

    private final List<Window> windows;

    /**
     * A schedule of those windows, put in order.
     *
     * @throws ValidationError when two of them overlap
     */
    public SiteSchedule(List<Window> windows) {
        List<Window> sorted = windows.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            Window earlier = sorted.get(i - 1);
            Window later = sorted.get(i);
            if (earlier.overlaps(later)) {
                throw new ValidationError(
                        "two granted windows overlap",
                        Context.of("first", earlier.toString(), "second", later.toString()));
            }
        }
        this.windows = sorted;
    }

    /** The windows, in order. */
    public List<Window> windows() {
        return windows;
    }

    /** The window open at that moment, if one is. */
    public Optional<Window> windowAt(LocalDateTime moment) {
        return windows.stream().filter(window -> window.contains(moment)).findFirst();
    }

    /** Whether the area can be clear at that moment. */
    public boolean isOpenAt(LocalDateTime moment) {
        return windowAt(moment).isPresent();
    }

    /** The first window that starts after that moment, if there is one. */
    public Optional<Window> nextWindowAfter(LocalDateTime moment) {
        return windows.stream()
                .filter(window -> window.startsAt().isAfter(moment))
                .findFirst();
    }

    /**
     * The first window with room for a job that long, starting no earlier than that moment. A window
     * already open counts from the moment; one that has closed does not count.
     */
    public Optional<Window> firstWindowFitting(Duration needed, LocalDateTime after) {
        return windows.stream()
                .filter(window -> window.endsAt().isAfter(after))
                .filter(window -> window.fits(needed, after.isAfter(window.startsAt()) ? after : window.startsAt()))
                .findFirst();
    }

    /** How much clear time there is on one calendar day, counting only that day's share of each window. */
    public Duration totalOpen(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long total = 0;
        for (Window window : windows) {
            LocalDateTime overlapStart = window.startsAt().isAfter(start) ? window.startsAt() : start;
            LocalDateTime overlapEnd = window.endsAt().isBefore(end) ? window.endsAt() : end;
            if (overlapEnd.isAfter(overlapStart)) {
                total += Window.millisBetween(overlapStart, overlapEnd);
            }
        }
        return new Duration(total);
    }
}
