package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * What the hole table says for one wall thickness on one side.
 *
 * @param thickness the nominal single wall thickness the table was read at
 * @param side the side the plaque lies on
 * @param indicator the plaque
 * @param hole the essential hole
 */
public record HoleSelection(Length thickness, IndicatorSide side, HoleIndicator indicator, HoleSize hole)
        implements IndicatorSelection {

    /** Whether the plaque has to carry a lead F beside it. */
    public boolean needsFMarker() {
        return side.needsMarking();
    }

    /** {@code 15 2T on the film side with an F marker}. */
    @Override
    public String describe() {
        String marker = needsFMarker() ? " with an F marker" : "";
        return indicator.label() + " " + hole + " on the " + side + marker;
    }
}
