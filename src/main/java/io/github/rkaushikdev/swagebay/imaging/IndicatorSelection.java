package io.github.rkaushikdev.swagebay.imaging;

/** What an indicator table says for one shot: a plaque and its hole, or a wire. */
public sealed interface IndicatorSelection permits HoleSelection, WireSelection {

    /** The selection the way the report reads. */
    String describe();
}
