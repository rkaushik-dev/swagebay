package io.github.rkaushikdev.swagebay;

import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.quantities.Units;
import java.util.List;

/**
 * The front door: the version, and the types a caller needs first, named in one place.
 *
 * <p>Nothing in the package takes a bare number. A wall is a {@link Length}, an activity an
 * {@link Activity}, an exposure a {@link Duration}, a dose a {@link Dose}. That is the single
 * decision everything else is built on, so the quantities and their short constructors (in
 * {@link Units}) come first, with the identities and the error hierarchy. This is a front door,
 * not a facade: reaching past it into a package is expected and supported.
 */
public final class Swagebay {

    /** The release, as the command line, the service and its API document report it. */
    public static final String VERSION = "1.0.0";

    /** The types published at the front door, sorted by simple name so a diff of the list stays readable. */
    public static final List<Class<?>> PUBLISHED = List.of(
            Activity.class,
            ClockPosition.class,
            ConfigurationError.class,
            ConflictError.class,
            Dose.class,
            DoseRate.class,
            Duration.class,
            JobNumber.class,
            Length.class,
            NotFoundError.class,
            QualificationError.class,
            SafetyError.class,
            SwagebayError.class,
            TechniqueError.class,
            Units.class,
            ValidationError.class,
            WeldId.class);

    private Swagebay() {}
}
