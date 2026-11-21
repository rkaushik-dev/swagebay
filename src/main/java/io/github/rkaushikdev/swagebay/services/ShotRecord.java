package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One exposure, as the crew reports it.
 *
 * @param weld the weld shot
 * @param exposureIndex which exposure of the weld it is, from one
 * @param exposedAt when the source was out
 * @param techniqueReference the technique sheet the shot was made to
 * @param evidence what the reader measured on the image, which the rules grade
 */
public record ShotRecord(
        WeldId weld, int exposureIndex, LocalDateTime exposedAt, String techniqueReference, Evidence evidence) {

    /**
     * A shot.
     *
     * @throws ValidationError for an exposure numbered below one, or no technique named
     */
    public ShotRecord {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(exposedAt, "exposedAt");
        Objects.requireNonNull(evidence, "evidence");
        if (exposureIndex < 1) {
            throw new ValidationError("exposures are numbered from one");
        }
        if (Text.isBlank(techniqueReference)) {
            throw new ValidationError("a shot names the technique it was made to");
        }
    }
}
