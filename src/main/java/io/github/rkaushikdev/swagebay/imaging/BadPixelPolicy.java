package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;

/**
 * What a client specification allows of a bad pixel map.
 *
 * <p>The limits are a policy, not a law: every client specification sets its own, so the map takes
 * them as an argument rather than baking them in.
 *
 * @param maxFraction the largest share of the panel that may be bad, in [0, 1]
 * @param maxCluster the largest compact cluster allowed, in pixels
 * @param maxLine the longest bad line allowed, in pixels
 */
public record BadPixelPolicy(BigDecimal maxFraction, int maxCluster, int maxLine) {

    /** Two percent of the panel, clusters of five, lines of ten. */
    public static final BadPixelPolicy DEFAULT = new BadPixelPolicy(new BigDecimal("0.02"), 5, 10);

    /**
     * A policy.
     *
     * @throws ValidationError for a fraction outside [0, 1] or a limit under one pixel
     */
    public BadPixelPolicy {
        if (maxFraction.signum() < 0 || maxFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError("a bad pixel fraction sits in [0, 1]");
        }
        if (maxCluster < 1 || maxLine < 1) {
            throw new ValidationError("cluster and line limits are at least one pixel");
        }
    }
}
