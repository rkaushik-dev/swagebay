package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.util.Optional;

/**
 * What the reader could actually see of the indicator on one radiograph.
 *
 * <p>A radiograph is graded on a hole indicator or on a wire indicator, never both. For a hole the
 * question is whether the essential hole is visible; for a wire it is which wire was the finest
 * visible, and a finer one than required is fine.
 *
 * @param requiredHole the plaque the technique calls for, when graded on a hole indicator
 * @param requiredHoleSize the essential hole in that plaque
 * @param holeVisible whether the essential hole is visible
 * @param requiredWire the wire the technique calls for, when graded on a wire indicator
 * @param finestWireVisible the number of the finest wire the reader could see, if any
 */
public record IndicatorEvidence(
        Optional<HoleIndicator> requiredHole,
        Optional<HoleSize> requiredHoleSize,
        boolean holeVisible,
        Optional<WireIndicator> requiredWire,
        Optional<Integer> finestWireVisible) {

    /**
     * Evidence about one indicator family.
     *
     * @throws ValidationError when both families or neither are given, or a hole indicator has no
     *     essential hole
     */
    public IndicatorEvidence {
        boolean hole = requiredHole.isPresent();
        boolean wire = requiredWire.isPresent();
        if (hole == wire) {
            throw new ValidationError("a radiograph is graded on a hole indicator or a wire one, not both");
        }
        if (hole && requiredHoleSize.isEmpty()) {
            throw new ValidationError("a hole indicator needs an essential hole");
        }
    }

    /** What was seen of a hole indicator. */
    public static IndicatorEvidence hole(HoleIndicator required, HoleSize essential, boolean visible) {
        return new IndicatorEvidence(
                Optional.of(required), Optional.of(essential), visible, Optional.empty(), Optional.empty());
    }

    /** What was seen of a wire indicator: the finest wire visible, or none at all. */
    public static IndicatorEvidence wire(WireIndicator required, Optional<Integer> finestVisible) {
        return new IndicatorEvidence(Optional.empty(), Optional.empty(), false, Optional.of(required), finestVisible);
    }

    /** What was seen of a wire indicator, with the finest wire visible. */
    public static IndicatorEvidence wire(WireIndicator required, int finestVisible) {
        return wire(required, Optional.of(finestVisible));
    }

    /** Whether the indicator shows what the technique requires. */
    public boolean satisfied() {
        if (requiredHole.isPresent()) {
            return holeVisible;
        }
        return finestWireVisible
                .map(seen -> seen >= requiredWire.orElseThrow().number())
                .orElse(false);
    }

    /** Why the indicator does not satisfy the technique, as the report states it. */
    public String describeFailure() {
        if (requiredHole.isPresent()) {
            return "the " + requiredHoleSize.orElseThrow() + " hole in the "
                    + requiredHole.get().label() + " plaque is not visible";
        }
        String required = requiredWire.orElseThrow().isoLabel();
        return finestWireVisible
                .map(seen -> "the finest wire visible is W" + seen + ", " + required + " is required")
                .orElse("no wire is visible, " + required + " is required");
    }
}
