package io.github.rkaushikdev.swagebay.geometry;

/**
 * The ways to point a source at a weld, and every one of them is a compromise.
 *
 * <p>Panoramic puts the source in the middle of the pipe and shoots the whole circumference at
 * once: one exposure, one wall of steel, the best image anyone is going to get, and it needs access
 * to the bore. Single wall single image puts the source inside against the wall and the film
 * outside opposite it. Double wall single image is the outside job: the beam goes through the far
 * wall and the near wall and only the near wall is interpreted. Double wall double image is the
 * small bore job: both walls are on the film, offset into an ellipse or laid on top of each other.
 *
 * <p>The exposure counts are the code minimums for complete coverage of a girth weld, not a guess,
 * and they are the reason a small bore weld costs three times what its size suggests.
 */
public enum ShotType {
    PANORAMIC("panoramic", 1, 1, 360),
    SWSI("single wall single image", 1, 3, 120),
    DWSI("double wall single image", 2, 3, 120),
    DWDI_ELLIPTICAL("double wall double image, elliptical", 2, 2, 90),
    DWDI_SUPERIMPOSED("double wall double image, superimposed", 2, 3, 60),
    CONTACT("contact", 1, 1, 360);

    private final String value;
    private final int wallsPenetrated;
    private final int minimumExposures;
    private final int exposureSeparationDegrees;

    ShotType(String value, int wallsPenetrated, int minimumExposures, int exposureSeparationDegrees) {
        this.value = value;
        this.wallsPenetrated = wallsPenetrated;
        this.minimumExposures = minimumExposures;
        this.exposureSeparationDegrees = exposureSeparationDegrees;
    }

    /** The shot as the technique sheet names it. */
    public String value() {
        return value;
    }

    /** How many walls the beam crosses. */
    public int wallsPenetrated() {
        return wallsPenetrated;
    }

    /** How many walls are read off the film: two only for a double image. */
    public int wallsInterpreted() {
        return isDoubleImage() ? 2 : 1;
    }

    /** Whether the source has to go down the bore. */
    public boolean needsInternalAccess() {
        return this == PANORAMIC || this == SWSI;
    }

    /** The code minimum for complete coverage of a girth weld. */
    public int minimumExposures() {
        return minimumExposures;
    }

    /** How far apart round the pipe the minimum exposures are spaced. */
    public int exposureSeparationDegrees() {
        return exposureSeparationDegrees;
    }

    /** Whether both walls are on the film, elliptical or superimposed. */
    boolean isDoubleImage() {
        return this == DWDI_ELLIPTICAL || this == DWDI_SUPERIMPOSED;
    }

    @Override
    public String toString() {
        return value;
    }
}
