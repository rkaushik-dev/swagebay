package io.github.rkaushikdev.swagebay.technique;

import io.github.rkaushikdev.swagebay.geometry.DistanceRule;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.FilmSystem;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * What the office knows before it starts on a technique sheet.
 *
 * <p>Build one with {@link #builder}: the weld, the source, the imaging system, the film and the
 * moment of the shot are always known, and the rest starts at what a shop asks for when nobody says
 * otherwise.
 *
 * @param joint the weld
 * @param source the sealed source, decayed to the moment of the shot when the sheet is worked
 * @param system the imaging system the radiograph is made on
 * @param film the film the exposure is corrected to
 * @param shotAt when the shot is made, wall clock time
 * @param shot the shot asked for, or absent to let the planner choose one
 * @param screensFitted whether lead screens are in the cassette
 * @param collimatorKey the collimator fitted to the guide tube, if one is
 * @param indicatorSide which side of the weld the indicator goes on
 * @param window the clear time the client gives, when there is a limit
 * @param density the density the film is exposed to, which picks the chart
 * @param distanceRule the rule the procedure sets the source distance by
 */
public record TechniqueRequest(
        Joint joint,
        SealedSource source,
        ImagingSystem system,
        FilmSystem film,
        LocalDateTime shotAt,
        Optional<ShotType> shot,
        boolean screensFitted,
        Optional<String> collimatorKey,
        IndicatorSide indicatorSide,
        Optional<Duration> window,
        BigDecimal density,
        DistanceRule distanceRule) {

    /** The density a shop exposes to unless told otherwise. */
    public static final BigDecimal DEFAULT_DENSITY = new BigDecimal("2.0");

    /** A request; every part is required, the optional ones as {@link Optional}. */
    public TechniqueRequest {
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(film, "film");
        Objects.requireNonNull(shotAt, "shotAt");
        Objects.requireNonNull(shot, "shot");
        Objects.requireNonNull(collimatorKey, "collimatorKey");
        Objects.requireNonNull(indicatorSide, "indicatorSide");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(density, "density");
        Objects.requireNonNull(distanceRule, "distanceRule");
    }

    /**
     * A builder holding what every request has. The planner chooses the shot, screens are fitted,
     * no collimator, the indicator on the source side, no window, a density of 2.0 and the source
     * distance set by the Section V unsharpness limit.
     */
    public static Builder builder(
            Joint joint, SealedSource source, ImagingSystem system, FilmSystem film, LocalDateTime shotAt) {
        return new Builder(joint, source, system, film, shotAt);
    }

    /** Assembles a {@link TechniqueRequest} from the defaults a shop works to. */
    public static final class Builder {

        private Joint joint;
        private SealedSource source;
        private ImagingSystem system;
        private FilmSystem film;
        private LocalDateTime shotAt;
        private Optional<ShotType> shot = Optional.empty();
        private boolean screensFitted = true;
        private Optional<String> collimatorKey = Optional.empty();
        private IndicatorSide indicatorSide = IndicatorSide.SOURCE;
        private Optional<Duration> window = Optional.empty();
        private BigDecimal density = DEFAULT_DENSITY;
        private DistanceRule distanceRule = DistanceRule.ASME_V;

        private Builder(Joint joint, SealedSource source, ImagingSystem system, FilmSystem film, LocalDateTime shotAt) {
            this.joint = joint;
            this.source = source;
            this.system = system;
            this.film = film;
            this.shotAt = shotAt;
        }

        /** Another weld. */
        public Builder joint(Joint value) {
            joint = value;
            return this;
        }

        /** Another source. */
        public Builder source(SealedSource value) {
            source = value;
            return this;
        }

        /** Another imaging system. */
        public Builder system(ImagingSystem value) {
            system = value;
            return this;
        }

        /** Another film. */
        public Builder film(FilmSystem value) {
            film = value;
            return this;
        }

        /** Another moment for the shot. */
        public Builder shotAt(LocalDateTime value) {
            shotAt = value;
            return this;
        }

        /** The shot to make, rather than the one the planner would choose. */
        public Builder shot(ShotType value) {
            return shot(Optional.of(value));
        }

        /** The shot to make, or absent to let the planner choose. */
        public Builder shot(Optional<ShotType> value) {
            shot = value;
            return this;
        }

        /** Whether lead screens are in the cassette. */
        public Builder screensFitted(boolean value) {
            screensFitted = value;
            return this;
        }

        /** The collimator fitted, by its key. */
        public Builder collimatorKey(String value) {
            return collimatorKey(Optional.of(value));
        }

        /** The collimator fitted, by its key, or absent for a bare guide tube. */
        public Builder collimatorKey(Optional<String> value) {
            collimatorKey = value;
            return this;
        }

        /** Which side of the weld the indicator goes on. */
        public Builder indicatorSide(IndicatorSide value) {
            indicatorSide = value;
            return this;
        }

        /** The clear time the client gives. */
        public Builder window(Duration value) {
            return window(Optional.of(value));
        }

        /** The clear time the client gives, or absent when there is no limit. */
        public Builder window(Optional<Duration> value) {
            window = value;
            return this;
        }

        /** The density the film is exposed to. */
        public Builder density(BigDecimal value) {
            density = value;
            return this;
        }

        /** The rule the procedure sets the source distance by. */
        public Builder distanceRule(DistanceRule value) {
            distanceRule = value;
            return this;
        }

        /** The request as assembled. */
        public TechniqueRequest build() {
            return new TechniqueRequest(
                    joint,
                    source,
                    system,
                    film,
                    shotAt,
                    shot,
                    screensFitted,
                    collimatorKey,
                    indicatorSide,
                    window,
                    density,
                    distanceRule);
        }
    }
}
