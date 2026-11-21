package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Objects;

/**
 * One joint on a night's list and how long the source is out for it.
 *
 * @param weld the weld
 * @param sourceOut the source out time for the whole joint, positive
 */
public record Piece(String weld, Duration sourceOut) {

    /**
     * A piece of work.
     *
     * @throws ValidationError for a blank weld or no time at all
     */
    public Piece {
        Objects.requireNonNull(sourceOut, "sourceOut");
        if (Text.isBlank(weld)) {
            throw new ValidationError("a piece of work names a weld");
        }
        if (sourceOut.ms() <= 0) {
            throw new ValidationError("a piece of work takes some time", Context.of("weld", weld));
        }
    }
}
