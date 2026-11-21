package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Objects;

/**
 * The stretch of weld that was cut out for a repair.
 *
 * <p>The weld metal either side of a cut out is affected by the second thermal cycle and the code
 * wants it examined, so the re-shoot has to reach past the ends of the excavation by {@link
 * #REPAIR_MARGIN}.
 *
 * @param startsAt where the cut starts along the weld, not negative
 * @param endsAt where it ends, past the start
 */
public record Excavation(Length startsAt, Length endsAt) {

    /** How far past each end of the excavation the re-shoot has to reach. */
    public static final Length REPAIR_MARGIN = Length.fromMm(25);

    /**
     * A stretch along the weld.
     *
     * @param start where it starts
     * @param end where it ends
     */
    public record Stretch(Length start, Length end) {}

    /**
     * An excavation.
     *
     * @throws ValidationError for a negative start or a length that is not positive
     */
    public Excavation {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (startsAt.um() < 0) {
            throw new ValidationError("an excavation starts somewhere on the weld");
        }
        if (endsAt.compareTo(startsAt) <= 0) {
            throw new ValidationError("an excavation has a positive length");
        }
    }

    /** How long the cut is. */
    public Length length() {
        return endsAt.minus(startsAt);
    }

    /** The stretch the re-shoot has to cover, margin included, held at the start of the weld. */
    public Stretch coverageRequired() {
        Length start = new Length(Math.max(0, startsAt.um() - REPAIR_MARGIN.um()));
        return new Stretch(start, endsAt.plus(REPAIR_MARGIN));
    }
}
