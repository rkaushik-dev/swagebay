package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A stretch of a circumference, clockwise from its start, the way one film covers it.
 *
 * <p>An arc carries its own wrap: one from 330 degrees for 60 crosses twelve o'clock and is one
 * arc, not two. It is closed at its start and open at its end.
 *
 * @param label the film's label on the weld map
 * @param startsAt the start, in degrees clockwise from twelve
 * @param span how far round it reaches, over zero and at most a full turn
 */
public record Arc(String label, BigDecimal startsAt, BigDecimal span) {

    private static final BigDecimal WALK_STEP = new BigDecimal("0.5");

    /**
     * An arc.
     *
     * @throws ValidationError for a blank label, a span that is not positive, or one over a full turn
     */
    public Arc {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(span, "span");
        if (Text.isBlank(label)) {
            throw new ValidationError("an arc has a label");
        }
        if (span.signum() <= 0) {
            throw new ValidationError("an arc has a positive span", Context.of("label", label));
        }
        if (span.compareTo(WeldMap.FULL_CIRCLE) > 0) {
            throw new ValidationError("an arc cannot go round twice", Context.of("label", label));
        }
    }

    /** Where the arc ends, folded into one turn. */
    public BigDecimal endsAt() {
        return WeldMap.wrap(Decimals.add(startsAt, span));
    }

    /** Whether the arc crosses twelve o'clock. */
    public boolean wraps() {
        return Decimals.add(startsAt, span).compareTo(WeldMap.FULL_CIRCLE) > 0;
    }

    /** Whether the arc reaches that bearing: from its start inclusive to its end exclusive. */
    public boolean contains(BigDecimal bearing) {
        BigDecimal offset = WeldMap.wrap(Decimals.subtract(bearing, startsAt));
        return offset.compareTo(span) < 0;
    }

    /** How much of the circle both arcs cover, walked round in half degrees rather than sorted. */
    public BigDecimal overlapWith(Arc other) {
        BigDecimal covered = BigDecimal.ZERO;
        BigDecimal bearing = BigDecimal.ZERO;
        while (bearing.compareTo(WeldMap.FULL_CIRCLE) < 0) {
            if (contains(bearing) && other.contains(bearing)) {
                covered = Decimals.add(covered, WALK_STEP);
            }
            bearing = Decimals.add(bearing, WALK_STEP);
        }
        return covered;
    }

    /** The arc as the sketch labels it, such as 2 120&deg; for 130&deg;. */
    public String describe() {
        return label + " " + startsAt + WeldMap.DEGREE + " for " + span + WeldMap.DEGREE;
    }
}
