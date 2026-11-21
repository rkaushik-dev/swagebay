package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Whatever the image is going on, described the way planning needs it.
 *
 * <p>Film, a phosphor plate and a detector panel are not interchangeable, and this type does not
 * pretend they are. It gives the planner one handle, because most of the technique is the same
 * whichever is used: the same geometry, the same indicator, the same coverage. Where they differ is
 * the gate applied afterwards, and the {@link Medium} decides which gate runs. A system that applied
 * both gates to everything would reject every radiograph ever made.
 *
 * @param key the key of the film, plate or panel
 * @param name its trade name
 * @param medium what the image is recorded on
 * @param basicSpatialResolution the finest feature the system resolves
 * @param speedIndex the film speed index; present exactly when the medium is film
 * @param normalisedSnr the normalised signal to noise; present for a plate or a panel
 */
public record ImagingSystem(
        String key,
        String name,
        Medium medium,
        Length basicSpatialResolution,
        Optional<BigDecimal> speedIndex,
        Optional<Integer> normalisedSnr) {

    /** What a fine grain film with lead screens resolves in practice. */
    public static final Length FILM_RESOLUTION = Length.fromMm("0.030");

    /**
     * An imaging system.
     *
     * @throws ValidationError for a resolution that is not positive, a film without a speed index or
     *     a digital system without a noise figure
     */
    public ImagingSystem {
        if (basicSpatialResolution.um() <= 0) {
            throw new ValidationError("an imaging system needs a resolution", Context.of("key", key));
        }
        if (medium.gradedOnDensity() && speedIndex.isEmpty()) {
            throw new ValidationError("a film system needs a speed index", Context.of("key", key));
        }
        if (medium.gradedOnNoise() && normalisedSnr.isEmpty()) {
            throw new ValidationError("a digital system needs a noise figure", Context.of("key", key));
        }
    }

    /** The system for a film, at {@link #FILM_RESOLUTION}. */
    public static ImagingSystem fromFilm(FilmSystem film) {
        return new ImagingSystem(
                film.key(),
                film.name(),
                Medium.FILM,
                FILM_RESOLUTION,
                Optional.of(film.speedIndex()),
                Optional.empty());
    }

    /** The system for a phosphor plate. */
    public static ImagingSystem fromPlate(PlateSystem plate) {
        return new ImagingSystem(
                plate.key(),
                plate.name(),
                Medium.PLATE,
                plate.basicSpatialResolution(),
                Optional.empty(),
                Optional.of(plate.normalisedSnr()));
    }

    /** The system for a detector panel. */
    public static ImagingSystem fromDetector(DetectorArray panel) {
        return new ImagingSystem(
                panel.key(),
                panel.name(),
                Medium.PANEL,
                panel.basicSpatialResolution(),
                Optional.empty(),
                Optional.of(panel.normalisedSnr()));
    }

    /** Whether the density gate runs on images from that system. */
    public static boolean densityApplies(ImagingSystem system) {
        return system.medium.gradedOnDensity();
    }

    /** Whether the noise gate runs on images from that system. */
    public static boolean noiseApplies(ImagingSystem system) {
        return system.medium.gradedOnNoise();
    }

    /** Which checks a radiograph on this system has to pass, in the order a report lists them. */
    public List<String> gates() {
        if (medium.gradedOnDensity()) {
            return List.of("density", "density variation", "indicator");
        }
        return List.of("normalised snr", "basic spatial resolution", "indicator");
    }

    /** Whether a feature that size is at or above the system's resolution. */
    public boolean resolves(Length feature) {
        return feature.compareTo(basicSpatialResolution) >= 0;
    }

    /** {@code Structurix D7 on film}. */
    @Override
    public String toString() {
        return name + " on " + medium;
    }
}
