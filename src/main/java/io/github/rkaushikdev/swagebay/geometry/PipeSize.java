package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * One row of the pipe schedule table, resolved.
 *
 * @param nominal the nominal pipe size, {@code 6} or {@code 1-1/2}
 * @param outsideDiameter the published outside diameter, which no schedule moves
 * @param schedule the wall schedule, upper case
 * @param wall the published wall at that schedule
 */
public record PipeSize(String nominal, Length outsideDiameter, String schedule, Length wall) {

    /** The bore: the outside diameter less two walls. */
    public Length insideDiameter() {
        return outsideDiameter.minus(wall.times(2));
    }

    /** The size the way a work order reads: {@code NPS 1-1/2 sch 80}. */
    @Override
    public String toString() {
        return "NPS " + nominal + " sch " + schedule;
    }
}
