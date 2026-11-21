package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.scheduling.SiteSchedule;
import io.github.rkaushikdev.swagebay.scheduling.Window;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fitting a night's work into the windows the client gave.
 *
 * <p>A shift is a list of joints and a list of windows, and the question is which joints get shot
 * tonight. It looks like a packing problem and it is not, because the order is not free: a crew
 * works its way along a rack and going back for one joint costs more than the joint is worth.
 *
 * <p>So the plan walks the joints in the order they were given, fits each one into the window that
 * is open when the crew reaches it, and stops when the window closes. Anything that did not fit
 * rolls to the next window and then to the next night, and the plan says which so the coordinator
 * can see what tomorrow looks like before tonight starts.
 *
 * <p>Setup time is counted once per window rather than once per joint. Roping off an area, walking
 * the boundary and posting it takes the same forty minutes whether one joint is shot inside it or
 * six, and a planner that charges it per joint under books every night.
 */
public final class Shifts {

    /** Roping off, walking the boundary and posting it, once per window. */
    public static final Duration DEFAULT_SETUP = Duration.fromMinutes(40);

    private Shifts() {}

    /** {@link #planShift(SiteSchedule, List, Duration)} with the usual forty minute setup. */
    public static ShiftPlan planShift(SiteSchedule schedule, List<Piece> work) {
        return planShift(schedule, work, DEFAULT_SETUP);
    }

    /**
     * Walk the joints in order and fit them into the windows in order. Each window loses the setup
     * first; a window too short for it is skipped, and the first joint that does not fit in what is
     * left of a window waits for the next one. A negative setup cannot be given: a duration below
     * zero is refused when it is made.
     */
    public static ShiftPlan planShift(SiteSchedule schedule, List<Piece> work, Duration setup) {
        Deque<Piece> remaining = new ArrayDeque<>(work);
        List<Slot> slots = new ArrayList<>();
        int used = 0;

        for (Window window : schedule.windows()) {
            if (remaining.isEmpty()) {
                break;
            }
            LocalDateTime clock = after(window.startsAt(), setup);
            if (!clock.isBefore(window.endsAt())) {
                continue;
            }
            int placedHere = 0;
            while (!remaining.isEmpty()) {
                Piece piece = remaining.peekFirst();
                LocalDateTime finish = after(clock, piece.sourceOut());
                if (finish.isAfter(window.endsAt())) {
                    break;
                }
                slots.add(new Slot(piece.weld(), window.startsAt(), clock, finish));
                clock = finish;
                placedHere++;
                remaining.removeFirst();
            }
            if (placedHere > 0) {
                used++;
            }
        }

        return new ShiftPlan(slots, remaining.stream().map(Piece::weld).toList(), used);
    }

    /** {@link #windowUtilisation(ShiftPlan, Window, Duration)} with the usual setup. */
    public static Duration windowUtilisation(ShiftPlan plan, Window window) {
        return windowUtilisation(plan, window, DEFAULT_SETUP);
    }

    /** How much of one window the plan actually uses, setup included; nothing for a window it does not use. */
    public static Duration windowUtilisation(ShiftPlan plan, Window window, Duration setup) {
        List<Slot> inside = plan.slots().stream()
                .filter(slot -> slot.windowStartsAt().equals(window.startsAt()))
                .toList();
        if (inside.isEmpty()) {
            return Duration.ZERO;
        }
        long total = inside.stream().mapToLong(slot -> slot.length().ms()).sum();
        return new Duration(total + setup.ms());
    }

    /** {@link #spareIn(ShiftPlan, Window, Duration)} with the usual setup. */
    public static Duration spareIn(ShiftPlan plan, Window window) {
        return spareIn(plan, window, DEFAULT_SETUP);
    }

    /** What is left of a window after the plan's use of it, never below zero. */
    public static Duration spareIn(ShiftPlan plan, Window window, Duration setup) {
        Duration used = windowUtilisation(plan, window, setup);
        if (used.ms() >= window.length().ms()) {
            return Duration.ZERO;
        }
        return new Duration(window.length().ms() - used.ms());
    }

    private static LocalDateTime after(LocalDateTime moment, Duration span) {
        return moment.plus(span.ms(), ChronoUnit.MILLIS);
    }
}
