package io.github.rkaushikdev.swagebay.testing;

import io.github.rkaushikdev.swagebay.imaging.Calibration;
import io.github.rkaushikdev.swagebay.imaging.Density;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.HoleIndicator;
import io.github.rkaushikdev.swagebay.imaging.HoleSize;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.imaging.IndicatorEvidence;
import io.github.rkaushikdev.swagebay.imaging.Reading;
import io.github.rkaushikdev.swagebay.imaging.TechniqueClass;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared imaging evidence for the tests that grade, record or report a radiograph.
 *
 * <p>One sound piece of film, so a test that passes in one class and fails in another is a
 * difference in the rules and never a difference in the fixture: Structurix D7, a 30 plaque with
 * its 2T hole, class A, gamma, the same density through the indicator and the weld, and a
 * densitometer checked at the moment of reading.
 */
public final class ImagingFixtures {

    /** When the fixture film is read, and its densitometer checked, unless a test says otherwise. */
    public static final LocalDateTime FILM_READ_AT = LocalDateTime.of(2024, 11, 5, 23, 0);

    private ImagingFixtures() {}

    /** A sound film, 26 mm penetrated, the hole visible, reading 2.50. */
    public static Evidence filmEvidence() {
        return filmEvidence(26, true, "2.50", FILM_READ_AT);
    }

    /** The sound film with the essential hole visible or not. */
    public static Evidence filmEvidence(boolean visible) {
        return filmEvidence(26, visible, "2.50", FILM_READ_AT);
    }

    /** The sound film reading that density through both the indicator and the weld. */
    public static Evidence filmEvidence(String density) {
        return filmEvidence(26, true, density, FILM_READ_AT);
    }

    /** The sound film read, and its densitometer checked, at that moment. */
    public static Evidence filmEvidence(LocalDateTime at) {
        return filmEvidence(26, true, "2.50", at);
    }

    /** The sound film with the hole visible or not, read at that moment. */
    public static Evidence filmEvidence(boolean visible, LocalDateTime at) {
        return filmEvidence(26, visible, "2.50", at);
    }

    /**
     * A piece of film with every part named.
     *
     * @param penetratedMm the penetrated thickness in whole millimetres
     * @param visible whether the 2T hole of the 30 plaque is visible
     * @param density the density read through the indicator and through the weld
     * @param at when the film was read and the densitometer checked
     */
    public static Evidence filmEvidence(long penetratedMm, boolean visible, String density, LocalDateTime at) {
        BigDecimal reading = new BigDecimal(density);
        return Evidence.builder(
                        ImagingSystem.fromFilm(Films.film("d7")),
                        Length.fromMm(penetratedMm),
                        TechniqueClass.A,
                        IndicatorEvidence.hole(new HoleIndicator(30), HoleSize.TWO_T, visible))
                .density(Density.grade(
                        List.of(new Reading("indicator", reading, true), new Reading("weld", reading)),
                        true,
                        new Calibration(at, new BigDecimal("2.00"), new BigDecimal("2.01"), "X-Rite"),
                        at))
                .build();
    }
}
