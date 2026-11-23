package io.github.rkaushikdev.swagebay.imaging;

/** What an indicator table says for one shot: a plaque and its hole, an ISO wire, or an ASTM wire. */
public sealed interface IndicatorSelection permits AstmWireSelection, HoleSelection, WireSelection {

    /** The selection the way the report reads. */
    String describe();
}
