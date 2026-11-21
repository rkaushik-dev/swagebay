package io.github.rkaushikdev.swagebay.reporting;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the radiograph schedule.
 *
 * @param number the radiograph number
 * @param weld the weld it was shot of, repair round included: {@code GW-104R1}
 * @param exposureIndex which exposure of the weld it is
 * @param exposedOn the day it was shot
 * @param techniqueReference the technique it was shot to
 * @param medium what the image is recorded on
 * @param admissible whether it is evidence of anything
 * @param readBy who read it, absent until somebody has
 * @param indications how many indications were called on it
 */
public record RadiographLine(
        String number,
        String weld,
        int exposureIndex,
        LocalDate exposedOn,
        String techniqueReference,
        String medium,
        boolean admissible,
        Optional<String> readBy,
        int indications) {

    /** A line. */
    public RadiographLine {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(exposedOn, "exposedOn");
        Objects.requireNonNull(techniqueReference, "techniqueReference");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(readBy, "readBy");
    }
}
