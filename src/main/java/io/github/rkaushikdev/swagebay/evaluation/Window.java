package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * The worst stretch of weld for an aggregate rule, and what is in it.
 *
 * @param startsAt where the window starts along the weld
 * @param endsAt where it ends
 * @param aggregate the length of weld affected inside it, overlap counted once
 * @param count how many indications reach into it
 */
public record Window(Length startsAt, Length endsAt, Length aggregate, int count) {

    /** How long the window is. */
    public Length length() {
        return endsAt.minus(startsAt);
    }

    /** The window as the report reads: {@code 20.0 mm in the 305 mm from 0 mm}. */
    public String describe() {
        return aggregate.formatMm(1) + " in the " + length().formatMm(0) + " from " + startsAt.formatMm(0);
    }
}
