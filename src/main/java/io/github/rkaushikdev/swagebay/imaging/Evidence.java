package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.Optional;

/**
 * Everything the reader wrote down about one radiograph.
 *
 * <p>Which parts matter depends on the medium: a film is graded on its density verdict, a plate or
 * a panel on its noise measurement and, when the technique states a required resolution, on the
 * duplex gauge reading. Build one with {@link #builder}.
 *
 * @param system what the image was recorded on
 * @param penetrated the thickness the beam crossed
 * @param techniqueClass the technique class the shot was planned in
 * @param indicator what was seen of the indicator
 * @param density the density verdict, for film
 * @param noise the signal to noise measurement, for a plate or a panel
 * @param duplexReading the finest duplex pair the reader resolved
 * @param requiredResolution the basic spatial resolution the technique requires, when it states one
 * @param notes anything else the reader wrote down
 */
public record Evidence(
        ImagingSystem system,
        Length penetrated,
        TechniqueClass techniqueClass,
        IndicatorEvidence indicator,
        Optional<DensityVerdict> density,
        Optional<NoiseMeasurement> noise,
        Optional<Integer> duplexReading,
        Optional<Length> requiredResolution,
        List<String> notes) {

    /** Evidence; the notes are copied. */
    public Evidence {
        notes = List.copyOf(notes);
    }

    /** A builder holding the four things every radiograph has, and nothing else yet. */
    public static Builder builder(
            ImagingSystem system, Length penetrated, TechniqueClass techniqueClass, IndicatorEvidence indicator) {
        return new Builder(system, penetrated, techniqueClass, indicator);
    }

    /** A builder holding everything this evidence holds, to change a part of it. */
    public Builder toBuilder() {
        return new Builder(system, penetrated, techniqueClass, indicator)
                .density(density)
                .noise(noise)
                .duplexReading(duplexReading)
                .requiredResolution(requiredResolution)
                .notes(notes);
    }

    /** Assembles {@link Evidence} one part at a time; every optional part starts absent. */
    public static final class Builder {

        private ImagingSystem system;
        private Length penetrated;
        private TechniqueClass techniqueClass;
        private IndicatorEvidence indicator;
        private Optional<DensityVerdict> density = Optional.empty();
        private Optional<NoiseMeasurement> noise = Optional.empty();
        private Optional<Integer> duplexReading = Optional.empty();
        private Optional<Length> requiredResolution = Optional.empty();
        private List<String> notes = List.of();

        private Builder(
                ImagingSystem system, Length penetrated, TechniqueClass techniqueClass, IndicatorEvidence indicator) {
            this.system = system;
            this.penetrated = penetrated;
            this.techniqueClass = techniqueClass;
            this.indicator = indicator;
        }

        /** What the image was recorded on. */
        public Builder system(ImagingSystem value) {
            system = value;
            return this;
        }

        /** The thickness the beam crossed. */
        public Builder penetrated(Length value) {
            penetrated = value;
            return this;
        }

        /** The technique class the shot was planned in. */
        public Builder techniqueClass(TechniqueClass value) {
            techniqueClass = value;
            return this;
        }

        /** What was seen of the indicator. */
        public Builder indicator(IndicatorEvidence value) {
            indicator = value;
            return this;
        }

        /** The density verdict. */
        public Builder density(DensityVerdict value) {
            return density(Optional.of(value));
        }

        /** The density verdict, or none. */
        public Builder density(Optional<DensityVerdict> value) {
            density = value;
            return this;
        }

        /** The signal to noise measurement. */
        public Builder noise(NoiseMeasurement value) {
            return noise(Optional.of(value));
        }

        /** The signal to noise measurement, or none. */
        public Builder noise(Optional<NoiseMeasurement> value) {
            noise = value;
            return this;
        }

        /** The finest duplex pair the reader resolved. */
        public Builder duplexReading(int value) {
            return duplexReading(Optional.of(value));
        }

        /** The finest duplex pair the reader resolved, or none. */
        public Builder duplexReading(Optional<Integer> value) {
            duplexReading = value;
            return this;
        }

        /** The basic spatial resolution the technique requires. */
        public Builder requiredResolution(Length value) {
            return requiredResolution(Optional.of(value));
        }

        /** The basic spatial resolution the technique requires, or none stated. */
        public Builder requiredResolution(Optional<Length> value) {
            requiredResolution = value;
            return this;
        }

        /** Anything else the reader wrote down. */
        public Builder notes(List<String> value) {
            notes = value;
            return this;
        }

        /** The evidence as assembled. */
        public Evidence build() {
            return new Evidence(
                    system,
                    penetrated,
                    techniqueClass,
                    indicator,
                    density,
                    noise,
                    duplexReading,
                    requiredResolution,
                    notes);
        }
    }
}
