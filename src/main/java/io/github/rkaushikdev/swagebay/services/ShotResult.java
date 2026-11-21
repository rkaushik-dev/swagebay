package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import java.util.Objects;

/**
 * What the shooting service issued and decided.
 *
 * @param number the radiograph number the shot was given
 * @param admissibility whether the image is evidence of anything, and why not
 */
public record ShotResult(String number, Admissibility admissibility) {

    /** A result. */
    public ShotResult {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(admissibility, "admissibility");
    }

    /** Whether the crew has to shoot it again. */
    public boolean needsReshooting() {
        return !admissibility.admissible();
    }
}
