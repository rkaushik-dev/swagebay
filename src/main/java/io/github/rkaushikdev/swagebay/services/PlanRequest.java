package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.technique.TechniqueRequest;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The decisions a shot plan needs that are not on the record: which source, which film, which shot,
 * and what night. Build one with {@link #builder}.
 *
 * @param weld the weld on the job to plan for
 * @param sourceSerial the source it is planned for
 * @param filmKey the film it is planned for
 * @param shotAt when the shot is made, which the source is decayed to
 * @param shot the shot asked for, or absent to let the planner choose one
 * @param indicatorSide which side of the weld the indicator goes on
 * @param window the clear time the client gives, when there is a limit
 * @param density the density the film is exposed to
 */
public record PlanRequest(
        WeldId weld,
        String sourceSerial,
        String filmKey,
        LocalDateTime shotAt,
        Optional<ShotType> shot,
        IndicatorSide indicatorSide,
        Optional<Duration> window,
        BigDecimal density) {

    /**
     * A request.
     *
     * @throws ValidationError when it names no source or no film
     */
    public PlanRequest {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(shotAt, "shotAt");
        Objects.requireNonNull(shot, "shot");
        Objects.requireNonNull(indicatorSide, "indicatorSide");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(density, "density");
        if (Text.isBlank(sourceSerial)) {
            throw new ValidationError("a plan names the source it is planned for");
        }
        if (Text.isBlank(filmKey)) {
            throw new ValidationError("a plan names the film it is planned for");
        }
    }

    /**
     * A builder holding the four things every plan names. The planner chooses the shot, the
     * indicator goes on the source side, there is no window and the density is {@link
     * TechniqueRequest#DEFAULT_DENSITY}.
     */
    public static Builder builder(WeldId weld, String sourceSerial, String filmKey, LocalDateTime shotAt) {
        return new Builder(weld, sourceSerial, filmKey, shotAt);
    }

    /** The same plan on a different film, for comparing. */
    public PlanRequest onFilm(String otherFilmKey) {
        return new PlanRequest(weld, sourceSerial, otherFilmKey, shotAt, shot, indicatorSide, window, density);
    }

    /** Assembles a {@link PlanRequest} from the defaults a shop plans to. */
    public static final class Builder {

        private WeldId weld;
        private String sourceSerial;
        private String filmKey;
        private LocalDateTime shotAt;
        private Optional<ShotType> shot = Optional.empty();
        private IndicatorSide indicatorSide = IndicatorSide.SOURCE;
        private Optional<Duration> window = Optional.empty();
        private BigDecimal density = TechniqueRequest.DEFAULT_DENSITY;

        private Builder(WeldId weld, String sourceSerial, String filmKey, LocalDateTime shotAt) {
            this.weld = weld;
            this.sourceSerial = sourceSerial;
            this.filmKey = filmKey;
            this.shotAt = shotAt;
        }

        /** Another weld. */
        public Builder weld(WeldId value) {
            weld = value;
            return this;
        }

        /** Another source. */
        public Builder sourceSerial(String value) {
            sourceSerial = value;
            return this;
        }

        /** Another film. */
        public Builder filmKey(String value) {
            filmKey = value;
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

        /**
         * The request as assembled.
         *
         * @throws ValidationError when it names no source or no film
         */
        public PlanRequest build() {
            return new PlanRequest(weld, sourceSerial, filmKey, shotAt, shot, indicatorSide, window, density);
        }
    }
}
