package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * The rule that sets how close the source may come, which is not the same rule in every standard.
 *
 * <p>ASME Section V caps the geometric unsharpness in steps of material thickness and leaves the
 * distance to fall out of the formula. ISO 17636-1 does not mention unsharpness at all. It writes
 * the minimum source to object distance directly, as a multiple of the source size times the
 * object to film distance to the power two thirds, with the multiple doubled for class B. On a
 * thick wall shot with a big pellet the ISO class B distance is the longer of the two by a wide
 * margin, and a crew planning to the ASME number ends up with a film the ISO client rejects.
 */
public enum DistanceRule {
    ASME_V("ASME Section V", BigDecimal.ZERO),
    ISO_17636_CLASS_A("ISO 17636-1 class A", new BigDecimal("7.5")),
    ISO_17636_CLASS_B("ISO 17636-1 class B", new BigDecimal("15"));

    private static final BigDecimal TWO_THIRDS = Decimals.divide(2, 3);
    private static final BigDecimal UM_PER_MM = BigDecimal.valueOf(1000);

    private final String value;
    private final BigDecimal isoFactor;

    DistanceRule(String value, BigDecimal isoFactor) {
        this.value = value;
        this.isoFactor = isoFactor;
    }

    /** The rule the way a procedure names it. */
    public String value() {
        return value;
    }

    /** Whether the rule writes the distance directly rather than capping the unsharpness. */
    public boolean isIso() {
        return isoFactor.signum() > 0;
    }

    /**
     * The closest the source may be to the near surface of the weld, rounded up to the micrometre.
     *
     * <p>Under ASME it is the distance at which the unsharpness meets the limit for that thickness.
     * Under ISO it is the factor times the source size times the object to film distance in
     * millimetres to the two thirds. Either way a film against the part needs no distance at all,
     * which comes back as one micrometre.
     *
     * @throws ValidationError for a source size that is not positive, a negative gap, or a thickness
     *     that is not positive
     */
    public Length minimumSourceToObject(Length focalSize, Length objectToFilm, Length materialThickness) {
        if (focalSize.um() <= 0) {
            throw new ValidationError("a focal size has to be positive");
        }
        if (objectToFilm.um() < 0) {
            throw new ValidationError("object to film distance cannot be negative");
        }
        if (!isIso()) {
            return BeamGeometry.minimumSourceToObject(
                    focalSize, objectToFilm, BeamGeometry.unsharpnessLimit(materialThickness));
        }
        if (materialThickness.um() <= 0) {
            throw new ValidationError("a material thickness has to be positive");
        }
        if (objectToFilm.um() == 0) {
            return new Length(1);
        }
        BigDecimal gapMm = objectToFilm.mm();
        BigDecimal mm =
                Decimals.multiply(Decimals.multiply(isoFactor, focalSize.mm()), Decimals.power(gapMm, TWO_THIRDS));
        return new Length(Decimals.ceiling(Decimals.multiply(mm, UM_PER_MM)));
    }

    /** What the technique sheet writes in the distance box under this rule: the minimum plus the gap. */
    public Length minimumSourceToFilm(Length focalSize, Length objectToFilm, Length materialThickness) {
        return minimumSourceToObject(focalSize, objectToFilm, materialThickness).plus(objectToFilm);
    }

    @Override
    public String toString() {
        return value;
    }
}
