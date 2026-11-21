package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Objects;

/**
 * The film taken over a repair.
 *
 * <p>A re-shoot that covers exactly the excavation and no more is a finding, and a common one,
 * because it is what a crew does when the excavation is near the end of a film.
 *
 * @param weld the repair round it was shot over
 * @param coversFrom where the film starts along the weld
 * @param coversTo where it ends, past the start
 */
public record ReShoot(WeldId weld, Length coversFrom, Length coversTo) {

    /**
     * How far short a film is at each end of the stretch it had to cover.
     *
     * @param before how far short at the start, zero when it reaches
     * @param after how far short at the end, zero when it reaches
     */
    public record Shortfall(Length before, Length after) {}

    /**
     * A re-shoot.
     *
     * @throws ValidationError for a length that is not positive
     */
    public ReShoot {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(coversFrom, "coversFrom");
        Objects.requireNonNull(coversTo, "coversTo");
        if (coversTo.compareTo(coversFrom) <= 0) {
            throw new ValidationError("a re-shoot covers a positive length");
        }
    }

    /** Whether the film reaches past both ends of the excavation by the margin. */
    public boolean covers(Repair repair) {
        Excavation.Stretch required = repair.excavation().coverageRequired();
        return coversFrom.compareTo(required.start()) <= 0 && coversTo.compareTo(required.end()) >= 0;
    }

    /** How far short the film is at each end. Zero either side when it reaches. */
    public Shortfall shortfall(Repair repair) {
        Excavation.Stretch required = repair.excavation().coverageRequired();
        Length before = coversFrom.compareTo(required.start()) > 0
                ? new Length(coversFrom.um() - required.start().um())
                : Length.ZERO;
        Length after = coversTo.compareTo(required.end()) < 0
                ? new Length(required.end().um() - coversTo.um())
                : Length.ZERO;
        return new Shortfall(before, after);
    }
}
