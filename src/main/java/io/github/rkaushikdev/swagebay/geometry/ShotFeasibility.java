package io.github.rkaushikdev.swagebay.geometry;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether a shot type is available on a joint, and in plain words why not.
 *
 * @param shot the shot type
 * @param available whether it can be made on the joint
 * @param reason why it cannot, absent when it can
 */
public record ShotFeasibility(ShotType shot, boolean available, Optional<String> reason) {

    /** A feasibility. */
    public ShotFeasibility {
        Objects.requireNonNull(shot, "shot");
        Objects.requireNonNull(reason, "reason");
    }
}
