package io.github.rkaushikdev.swagebay.records;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The record of one radiograph: what it was shot to, what happened, and what was read off it.
 *
 * <p>Everything else in this package computes. This is where the results are pinned to a piece of
 * paper with a number on it, and the number is what a client, an auditor or a court will ask for in
 * four years. The three things it carries are easy to conflate and must not be. What was intended
 * is the technique sheet it was shot to. What happened is the exposure that was actually made and
 * when. What was read off it is the reader, the moment they read it, and the indications they wrote
 * down. A record that carries only the third is a record nobody can defend, because there is no way
 * to show the film was made the way the procedure says.
 *
 * @param number the radiograph number
 * @param weld the weld it covers
 * @param exposureIndex which exposure of the weld it is, from one
 * @param exposedAt when the exposure was made
 * @param techniqueReference the technique sheet it was shot to
 * @param admissibility whether the image may have an opinion about the weld
 * @param indications what the reader wrote down
 * @param readBy who read it, empty until somebody has
 * @param readAt when it was read, absent until it has been
 */
public record Radiograph(
        String number,
        WeldId weld,
        int exposureIndex,
        LocalDateTime exposedAt,
        String techniqueReference,
        Admissibility admissibility,
        List<Indication> indications,
        String readBy,
        Optional<LocalDateTime> readAt) {

    /**
     * A radiograph record; the indications are copied.
     *
     * @throws ValidationError for a blank number or technique reference, an exposure index under
     *     one, a reader without a moment or a moment without a reader, or indications nobody read
     */
    public Radiograph {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(exposedAt, "exposedAt");
        Objects.requireNonNull(techniqueReference, "techniqueReference");
        Objects.requireNonNull(admissibility, "admissibility");
        Objects.requireNonNull(readBy, "readBy");
        Objects.requireNonNull(readAt, "readAt");
        indications = List.copyOf(indications);
        if (Text.isBlank(number)) {
            throw new ValidationError("a radiograph needs a number");
        }
        if (exposureIndex < 1) {
            throw new ValidationError("exposures are numbered from one", Context.of("number", number));
        }
        if (Text.isBlank(techniqueReference)) {
            throw new ValidationError("a radiograph names the technique it was shot to", Context.of("number", number));
        }
        if (Text.isBlank(readBy) != readAt.isEmpty()) {
            throw new ValidationError("a reading has both a reader and a date", Context.of("number", number));
        }
        if (!indications.isEmpty() && readAt.isEmpty()) {
            throw new ValidationError("indications were written down by somebody", Context.of("number", number));
        }
    }

    /** A radiograph that has not been read yet. */
    public Radiograph(
            String number,
            WeldId weld,
            int exposureIndex,
            LocalDateTime exposedAt,
            String techniqueReference,
            Admissibility admissibility) {
        this(
                number,
                weld,
                exposureIndex,
                exposedAt,
                techniqueReference,
                admissibility,
                List.of(),
                "",
                Optional.empty());
    }

    /** Whether the image may have an opinion about the weld. */
    public boolean isAdmissible() {
        return admissibility.admissible();
    }

    /** Whether somebody has read it. */
    public boolean hasBeenRead() {
        return readAt.isPresent();
    }

    /** The record's state in a line: {@code R-001 read by 0142, 0 indications}. */
    public String describe() {
        if (!isAdmissible()) {
            return number + " not admissible: " + admissibility.describe();
        }
        if (!hasBeenRead()) {
            return number + " admissible, not yet read";
        }
        return number + " read by " + readBy + ", " + indications.size() + " indications";
    }
}
