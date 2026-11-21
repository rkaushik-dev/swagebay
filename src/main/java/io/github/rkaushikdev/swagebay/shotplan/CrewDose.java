package io.github.rkaushikdev.swagebay.shotplan;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * What one of the crew is expected to take on this job, and where they stand to take it.
 *
 * @param badge whose dose it is
 * @param expected the dose taken while the source is out
 * @param standOff how far from the source they stand
 */
public record CrewDose(String badge, Dose expected, Length standOff) {

    /** {@code 0142 1.6 uSv at 133.8 m}. */
    public String describe() {
        return badge + " " + expected.formatUsv() + " at " + standOff.formatM();
    }
}
