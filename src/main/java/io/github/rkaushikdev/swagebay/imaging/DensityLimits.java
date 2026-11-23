package io.github.rkaushikdev.swagebay.imaging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The density a film has to read under the standard the job is worked to.
 *
 * <p>Section V and ISO 17636-1 agree that a film has a floor and a ceiling and disagree about almost
 * everything else. Section V sets the floor by the source, lowers it for a composite viewing, caps
 * every film at 4.0 and then adds the variation rule: the weld may read no thinner than 85 percent
 * and no denser than 130 percent of the density through the indicator. ISO sets the floor by the
 * technique class, 2.0 for class A and 2.3 for class B, takes the ceiling from what the viewer can
 * push light through, and has no variation rule at all.
 *
 * @param standard the standard the limits come from, as the reader's sheet names it
 * @param floor the lowest density accepted
 * @param ceiling the highest density accepted
 * @param variationRule whether the readings are also held to the window round the indicator density
 */
public record DensityLimits(String standard, BigDecimal floor, BigDecimal ceiling, boolean variationRule) {

    /** The ISO 17636-1 floor for class A. */
    public static final BigDecimal ISO_FLOOR_CLASS_A = new BigDecimal("2.0");

    /** The ISO 17636-1 floor for class B. */
    public static final BigDecimal ISO_FLOOR_CLASS_B = new BigDecimal("2.3");

    /** The limits; every part is required. */
    public DensityLimits {
        Objects.requireNonNull(standard, "standard");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(ceiling, "ceiling");
    }

    /** Section V: the floor for the source and the viewing, the 4.0 ceiling and the variation rule. */
    public static DensityLimits sectionV(boolean gamma, boolean composite) {
        return new DensityLimits("ASME Section V", Films.densityFloor(gamma, composite), Films.DENSITY_CEILING, true);
    }

    /**
     * ISO 17636-1 in that class, read on that viewer: the class floor, and the densest film the
     * viewer supports to two places below.
     */
    public static DensityLimits iso17636(TechniqueClass techniqueClass, Viewer viewer) {
        BigDecimal floor = techniqueClass.isImproved() ? ISO_FLOOR_CLASS_B : ISO_FLOOR_CLASS_A;
        BigDecimal ceiling = viewer.supportedDensity().setScale(2, RoundingMode.DOWN);
        return new DensityLimits("ISO 17636-1 " + techniqueClass, floor, ceiling, false);
    }
}
