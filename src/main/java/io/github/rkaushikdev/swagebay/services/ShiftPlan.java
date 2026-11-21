package io.github.rkaushikdev.swagebay.services;

import java.util.List;

/**
 * What fits tonight and what does not.
 *
 * @param slots the joints placed, in the order shot
 * @param leftOver the welds that did not fit, in the order given, for the next night
 * @param windowsUsed how many windows had at least one joint placed in them
 */
public record ShiftPlan(List<Slot> slots, List<String> leftOver, int windowsUsed) {

    /** A plan; the lists are copied. */
    public ShiftPlan {
        slots = List.copyOf(slots);
        leftOver = List.copyOf(leftOver);
    }

    /** Whether every joint fitted. */
    public boolean isComplete() {
        return leftOver.isEmpty();
    }

    /** {@code 2 joints across 1 windows}, with {@code , 3 left over} when something did not fit. */
    public String describe() {
        if (isComplete()) {
            return slots.size() + " joints across " + windowsUsed + " windows";
        }
        return slots.size() + " joints across " + windowsUsed + " windows, " + leftOver.size() + " left over";
    }
}
