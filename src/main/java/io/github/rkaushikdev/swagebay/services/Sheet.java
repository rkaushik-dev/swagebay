package io.github.rkaushikdev.swagebay.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The whole morning, in the order it has to be worked through.
 *
 * @param day the morning it is for
 * @param stopsToday what stops work today
 * @param stopsThisWeek what stops work within the week
 * @param runningLate what is merely late
 */
public record Sheet(LocalDate day, List<Line> stopsToday, List<Line> stopsThisWeek, List<Line> runningLate) {

    /** A sheet; the lists are copied. */
    public Sheet {
        Objects.requireNonNull(day, "day");
        stopsToday = List.copyOf(stopsToday);
        stopsThisWeek = List.copyOf(stopsThisWeek);
        runningLate = List.copyOf(runningLate);
    }

    /** Every line, today's first, then this week's, then what is late. */
    public List<Line> lines() {
        List<Line> all = new ArrayList<>(stopsToday);
        all.addAll(stopsThisWeek);
        all.addAll(runningLate);
        return List.copyOf(all);
    }

    /** Whether there is nothing on the sheet. */
    public boolean isQuiet() {
        return lines().isEmpty();
    }

    /** {@code nothing outstanding on 2025-07-10}, or {@code 3 outstanding on 2025-07-10}. */
    public String describe() {
        if (isQuiet()) {
            return "nothing outstanding on " + day;
        }
        return lines().size() + " outstanding on " + day;
    }
}
