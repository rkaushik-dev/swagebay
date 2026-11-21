package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One round of cutting out and welding again.
 *
 * <p>A repair is a round of the same weld, not a new weld. The identifier carries the round and the
 * register keys on the weld as first made, so a shop cannot improve its numbers by renaming. That
 * is the whole reason the identifier grammar has an R suffix in it.
 *
 * @param weld the repair round of the weld, {@code GW-104R1}
 * @param excavation what was cut out
 * @param weldedBy who welded it again
 * @param weldedOn when
 * @param clientApproved whether the client said yes, which a second repair needs
 */
public record Repair(WeldId weld, Excavation excavation, String weldedBy, LocalDate weldedOn, boolean clientApproved) {

    /**
     * A repair.
     *
     * @throws ValidationError for a blank welder, or a weld identifier that is not a repair round
     */
    public Repair {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(excavation, "excavation");
        Objects.requireNonNull(weldedBy, "weldedBy");
        Objects.requireNonNull(weldedOn, "weldedOn");
        if (Text.isBlank(weldedBy)) {
            throw new ValidationError("a repair is signed by the welder");
        }
        if (!weld.isRepair()) {
            throw new ValidationError(
                    "a repair belongs to a repair round of the weld", Context.of("weld", weld.toString()));
        }
    }

    /** A repair the client has not approved. */
    public Repair(WeldId weld, Excavation excavation, String weldedBy, LocalDate weldedOn) {
        this(weld, excavation, weldedBy, weldedOn, false);
    }

    /** Which repair round this is, from one. */
    public int round() {
        return weld.repair();
    }
}
