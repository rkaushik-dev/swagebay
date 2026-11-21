package io.github.rkaushikdev.swagebay.shotplan;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the ground round the weld will and will not allow.
 *
 * @param clearRadius how far the clear ground runs from the weld, positive
 * @param window the clear time the client gives, when there is a limit
 * @param occupiedBeyond what is occupied past the clear ground, as the site describes it
 */
public record SiteConstraints(Length clearRadius, Optional<Duration> window, List<String> occupiedBeyond) {

    /**
     * Site constraints; the occupied places are copied.
     *
     * @throws ValidationError for a clear radius that is not positive
     */
    public SiteConstraints {
        Objects.requireNonNull(clearRadius, "clearRadius");
        Objects.requireNonNull(window, "window");
        occupiedBeyond = List.copyOf(occupiedBeyond);
        if (clearRadius.um() <= 0) {
            throw new ValidationError("a site has some clear ground or the job does not happen");
        }
    }

    /** Clear ground with no window and nothing occupied beyond it. */
    public SiteConstraints(Length clearRadius) {
        this(clearRadius, Optional.empty(), List.of());
    }

    /** Clear ground for the length of a window, nothing occupied beyond it. */
    public SiteConstraints(Length clearRadius, Duration window) {
        this(clearRadius, Optional.of(window), List.of());
    }
}
