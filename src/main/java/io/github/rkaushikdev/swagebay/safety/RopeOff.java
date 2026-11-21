package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The distances the crew actually walks out and pegs: down the beam and off it to the sides.
 *
 * @param downBeam the boundary along the beam axis
 * @param offBeam the boundary straight behind the source, which a collimator pulls in
 * @param kind the posting the rope marks
 */
public record RopeOff(Length downBeam, Length offBeam, BoundaryKind kind) {

    private static final BigDecimal DOWN_THE_BEAM = BigDecimal.ZERO;
    private static final BigDecimal OFF_THE_BEAM = BigDecimal.valueOf(180);

    /** A rope off. */
    public RopeOff {
        Objects.requireNonNull(downBeam, "downBeam");
        Objects.requireNonNull(offBeam, "offBeam");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * The restricted area rope off round a field, the two distances that go on the sketch.
     *
     * @throws TechniqueError for a field with no source in it
     */
    public static RopeOff of(RadiationField radiationField) {
        return of(radiationField, BoundaryKind.RESTRICTED);
    }

    /**
     * The rope off for a posting round a field.
     *
     * @throws TechniqueError for a field with no source in it
     */
    public static RopeOff of(RadiationField radiationField, BoundaryKind kind) {
        Length down = radiationField.boundary(kind, DOWN_THE_BEAM);
        Length off = radiationField.boundary(kind, OFF_THE_BEAM);
        if (down.um() == 0) {
            throw new TechniqueError("there is no source to rope off");
        }
        return new RopeOff(down, off, kind);
    }

    /** Whether the two distances differ, as they do with a collimator fitted. */
    public boolean isDirectional() {
        return !downBeam.equals(offBeam);
    }

    /**
     * The sketch note: {@code restricted area at 133.3 m}, or with a collimator fitted {@code
     * restricted area at 133.3 m down the beam and 32.6 m to the sides}.
     */
    public String describe() {
        if (!isDirectional()) {
            return kind + " at " + downBeam.formatM();
        }
        return kind + " at " + downBeam.formatM() + " down the beam and " + offBeam.formatM() + " to the sides";
    }
}
