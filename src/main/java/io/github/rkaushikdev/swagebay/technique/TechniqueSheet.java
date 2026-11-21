package io.github.rkaushikdev.swagebay.technique;

import io.github.rkaushikdev.swagebay.exposure.ExposureResult;
import io.github.rkaushikdev.swagebay.geometry.BeamGeometry;
import io.github.rkaushikdev.swagebay.geometry.CoveragePlan;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.HoleSelection;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;

/**
 * The plan, with everything that is wrong with it attached.
 *
 * <p>A sheet with warnings on it is a real thing that a Level III looks at and either signs or
 * sends back, so a sheet is produced for a plan that is merely bad, and the warnings travel with it.
 *
 * @param joint the weld
 * @param shot the shot, asked for or chosen
 * @param penetrated the thickness the beam crosses, which everything downstream is read off
 * @param geometry where the source, the weld and the film sit
 * @param sourceToFilm what goes in the distance box
 * @param coverage how many exposures the joint takes and how far apart
 * @param indicator the indicator and the hole that has to show
 * @param exposure the exposure calculation, every step of it
 * @param totalSourceOut how long the source is out over the whole joint
 * @param warnings everything wrong with the plan, in the order found
 */
public record TechniqueSheet(
        Joint joint,
        ShotType shot,
        Length penetrated,
        BeamGeometry geometry,
        Length sourceToFilm,
        CoveragePlan coverage,
        HoleSelection indicator,
        ExposureResult exposure,
        Duration totalSourceOut,
        List<String> warnings) {

    /** A sheet; the warnings are copied. */
    public TechniqueSheet {
        warnings = List.copyOf(warnings);
    }

    /** Whether there is nothing on the sheet for a Level III to read before signing. */
    public boolean isClean() {
        return warnings.isEmpty();
    }

    /** How many exposures the joint takes. */
    public int exposures() {
        return coverage.exposures();
    }

    /**
     * The sheet as one line for the job book: {@code carbon steel girth weld, 168.30 mm od, 10.97 mm
     * wall, double wall single image, 8 at 1162 mm, 30 2T on the source side, 0:37 each}.
     */
    public String describe() {
        return joint.describe() + ", " + shot + ", " + exposures() + " at " + sourceToFilm.formatMm(0) + ", "
                + indicator.describe() + ", " + exposure.timer() + " each";
    }
}
