package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Floats;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Where the source, the weld and the film are relative to each other, and the unsharpness that
 * falls out of it.
 *
 * <p>A source is not a point. It is a pellet three millimetres across, and every point on it casts
 * its own shadow of the same flaw in a slightly different place. The shadows add up to a blurred
 * edge whose width is the geometric unsharpness, and if it is wider than the flaw the flaw
 * disappears: {@code Ug = F x OFD / SOD}.
 *
 * <p>Source to film is what the tape measures, source to object is where the near surface of the
 * weld sits, and object to film is the rest. They add, and a geometry where they do not add is a
 * typing mistake, so the type refuses it rather than quietly computing something wrong.
 *
 * <p>The limits are the ASME Section V Article 2 table, keyed on material thickness, and they are
 * steps rather than a curve. A weld one hair over two inches gets a looser limit than one a hair
 * under, which looks arbitrary and is exactly what the code says.
 *
 * @param sourceToObject from the source to the near surface of the weld, positive
 * @param objectToFilm from the weld to the film, not negative
 * @param focalSize the source size, positive
 */
public record BeamGeometry(Length sourceToObject, Length objectToFilm, Length focalSize) {

    private record Step(Length ceiling, Length allowed) {}

    private static final List<Step> UNSHARPNESS_STEPS = List.of(
            new Step(Length.fromInch(2), Length.fromInch("0.020")),
            new Step(Length.fromInch(3), Length.fromInch("0.030")),
            new Step(Length.fromInch(4), Length.fromInch("0.040")));
    private static final Length UNSHARPNESS_ABOVE = Length.fromInch("0.070");

    /**
     * A geometry.
     *
     * @throws ValidationError for a source distance or focal size that is not positive, or a
     *     negative gap
     */
    public BeamGeometry {
        Objects.requireNonNull(sourceToObject, "sourceToObject");
        Objects.requireNonNull(objectToFilm, "objectToFilm");
        Objects.requireNonNull(focalSize, "focalSize");
        if (sourceToObject.um() <= 0) {
            throw new ValidationError("source to object distance has to be positive");
        }
        if (objectToFilm.um() < 0) {
            throw new ValidationError("object to film distance cannot be negative");
        }
        if (focalSize.um() <= 0) {
            throw new ValidationError("a focal size has to be positive");
        }
    }

    /**
     * Build from what the tape measured, which is source to film.
     *
     * @throws ValidationError when the object does not fit between the source and the film
     */
    public static BeamGeometry fromSfd(Length sfd, Length objectToFilm, Length focalSize) {
        Length sod = sfd.minus(objectToFilm);
        if (sod.um() <= 0) {
            throw new ValidationError(
                    "the object does not fit between the source and the film",
                    Context.of("sfd_um", sfd.um(), "ofd_um", objectToFilm.um()));
        }
        return new BeamGeometry(sod, objectToFilm, focalSize);
    }

    /**
     * The most blur the code will accept at that thickness.
     *
     * @throws ValidationError for a thickness that is not positive
     */
    public static Length unsharpnessLimit(Length materialThickness) {
        if (materialThickness.um() <= 0) {
            throw new ValidationError("a material thickness has to be positive");
        }
        for (Step step : UNSHARPNESS_STEPS) {
            if (materialThickness.compareTo(step.ceiling()) < 0) {
                return step.allowed();
            }
        }
        return UNSHARPNESS_ABOVE;
    }

    /**
     * The closest the source can be and still make the limit.
     *
     * <p>Rearranged from the unsharpness formula and rounded up, because a distance rounded down is
     * a shot that fails the very check it was computed to pass. With the film against the part
     * there is no requirement at all, which comes back as one micrometre.
     *
     * @throws ValidationError for a limit that is not positive
     */
    public static Length minimumSourceToObject(Length focalSize, Length objectToFilm, Length limit) {
        if (limit.um() <= 0) {
            throw new ValidationError("an unsharpness limit has to be positive");
        }
        if (objectToFilm.um() == 0) {
            return new Length(1);
        }
        BigDecimal exact = Decimals.divide(
                BigDecimal.valueOf(Math.multiplyExact(focalSize.um(), objectToFilm.um())),
                BigDecimal.valueOf(limit.um()));
        return new Length(Decimals.ceiling(exact));
    }

    /** What the technique sheet writes in the distance box: the minimum source distance plus the gap. */
    public static Length minimumSourceToFilm(Length focalSize, Length objectToFilm, Length materialThickness) {
        return DistanceRule.ASME_V.minimumSourceToFilm(focalSize, objectToFilm, materialThickness);
    }

    /** Source to object plus object to film. */
    public Length sourceToFilm() {
        return sourceToObject.plus(objectToFilm);
    }

    /** The focal size scaled by the gap over the source distance, to the nearest micrometre. */
    public Length geometricUnsharpness() {
        return new Length(unsharpnessUm(focalSize, objectToFilm, sourceToObject));
    }

    /** Source to film over source to object. */
    public BigDecimal magnification() {
        return sourceToFilm().ratio(sourceToObject);
    }

    /** Whether the unsharpness is inside the limit for that thickness. */
    public boolean meets(Length materialThickness) {
        return meets(DistanceRule.ASME_V, materialThickness);
    }

    /**
     * Whether the geometry satisfies that rule at that thickness: the unsharpness inside the limit
     * under ASME, the source at least the minimum distance off the weld under ISO.
     */
    public boolean meets(DistanceRule rule, Length materialThickness) {
        if (rule.isIso()) {
            return sourceToObject.compareTo(rule.minimumSourceToObject(focalSize, objectToFilm, materialThickness))
                    >= 0;
        }
        return geometricUnsharpness().compareTo(unsharpnessLimit(materialThickness)) <= 0;
    }

    /**
     * Refuse a geometry whose unsharpness is over the limit for that thickness.
     *
     * @throws TechniqueError naming the unsharpness, the limit and the source distance
     */
    public void check(Length materialThickness) {
        Length limit = unsharpnessLimit(materialThickness);
        Length actual = geometricUnsharpness();
        if (actual.compareTo(limit) > 0) {
            throw new TechniqueError(
                    "geometric unsharpness is over the limit for this thickness",
                    Context.of(
                            "unsharpness_mm", actual.mm().toString(),
                            "limit_mm", limit.mm().toString(),
                            "sod_mm", sourceToObject.mm().toString()));
        }
    }

    /**
     * {@code F x OFD / SOD} in micrometres, the quotient taken as the nearest double and then
     * rounded to the nearest whole number, a tie going to the even one.
     */
    static long unsharpnessUm(Length focalSize, Length objectToFilm, Length sourceToObject) {
        double product = Math.multiplyExact(focalSize.um(), objectToFilm.um());
        return Floats.round(product / sourceToObject.um());
    }
}
