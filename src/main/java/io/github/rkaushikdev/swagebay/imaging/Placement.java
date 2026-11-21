package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import java.util.Objects;
import java.util.Optional;

/**
 * One indicator, where it sits and what is under it.
 *
 * @param side which side of the part it lies on
 * @param position where it sits round the pipe, absent on a straight weld
 * @param shim the shim under it, absent on a weld ground flush
 */
public record Placement(IndicatorSide side, Optional<ClockPosition> position, Optional<Shim> shim) {

    /** A placement. */
    public Placement {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(shim, "shim");
    }

    /** Whether a lead F goes beside the indicator, which it does on the film side. */
    public boolean needsFMarker() {
        return side.needsMarking();
    }

    /** The placement as the sheet reads: {@code source side at 12:00 on a 2.50 mm carbon_steel shim}. */
    public String describe() {
        String where = position.map(at -> " at " + at).orElse("");
        String under = shim.map(pad -> " on a " + pad).orElse("");
        String marker = needsFMarker() ? " with an F marker" : "";
        return side + where + under + marker;
    }
}
