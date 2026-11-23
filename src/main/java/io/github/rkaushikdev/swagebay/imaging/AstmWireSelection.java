package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * What the wire column of the ASME table says for one wall thickness on one side.
 *
 * @param thickness the nominal single wall thickness the table was read at
 * @param side the side the wire set lies on
 * @param wire the essential wire
 */
public record AstmWireSelection(Length thickness, IndicatorSide side, AstmWire wire) implements IndicatorSelection {

    /** Whether the set has to carry a lead F beside it. */
    public boolean needsFMarker() {
        return side.needsMarking();
    }

    /** {@code wire 8 on the film side with an F marker, set B}. */
    @Override
    public String describe() {
        String marker = needsFMarker() ? " with an F marker" : "";
        return wire + " on the " + side + marker + ", set " + String.join("/", wire.sets());
    }
}
