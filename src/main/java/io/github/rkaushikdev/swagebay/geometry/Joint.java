package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Material;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Floats;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A weld joint, described the way the shooter needs it described.
 *
 * <p>Everything downstream needs the same four numbers about a weld and they are never all on the
 * work order. Outside diameter and wall come off the line list. Reinforcement is what the welder
 * actually left, which is a measurement and not a specification. The form of the joint decides
 * which shots are even legal: a girth weld in a six inch line can be shot through one wall from
 * inside if there is access, or through two walls from outside if there is not, and a one inch
 * line has no choice at all.
 *
 * <p>Diameter and wall together are the number that matters most. The ratio decides whether the
 * source can be put close enough for the geometric unsharpness to come out, and the small bore
 * threshold at 88.9 mm outside diameter is where the elliptical technique starts being the only
 * thing that works.
 *
 * <p>Plate has no diameter. The type carries that as an absence rather than a zero, because a zero
 * diameter would sail through arithmetic and come out as an infinite circumference somewhere three
 * packages away. Build a pipe joint with {@link #builder}.
 *
 * @param form what kind of weld this is
 * @param wall the nominal wall thickness
 * @param material what the joint is made of
 * @param outsideDiameter the outside diameter, absent for plate
 * @param reinforcement the cap height the welder left, zero when ground flush
 * @param seamLength the length of a longitudinal seam, where one is recorded
 */
public record Joint(
        JointForm form,
        Length wall,
        Material material,
        Optional<Length> outsideDiameter,
        Length reinforcement,
        Optional<Length> seamLength) {

    /** Three and a half inch outside diameter, where elliptical shots take over. */
    public static final Length SMALL_BORE_LIMIT = Length.fromMm("88.9");

    /**
     * A joint.
     *
     * @throws ValidationError for a wall that is not positive, a negative cap, plate given a
     *     diameter, pipe without one, or a wall that closes the bore
     */
    public Joint {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(wall, "wall");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(outsideDiameter, "outsideDiameter");
        Objects.requireNonNull(reinforcement, "reinforcement");
        Objects.requireNonNull(seamLength, "seamLength");
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive", Context.of("wall_um", wall.um()));
        }
        if (reinforcement.um() < 0) {
            throw new ValidationError("reinforcement cannot be negative", Context.of("um", reinforcement.um()));
        }
        if (form == JointForm.PLATE_BUTT) {
            if (outsideDiameter.isPresent()) {
                throw new ValidationError("plate has no outside diameter");
            }
        } else {
            if (outsideDiameter.isEmpty()) {
                throw new ValidationError("a pipe joint needs an outside diameter");
            }
            Length od = outsideDiameter.get();
            if (od.um() <= 0) {
                throw new ValidationError("an outside diameter has to be positive");
            }
            if (wall.um() * 2 >= od.um()) {
                throw new ValidationError(
                        "the wall closes the bore", Context.of("wall_um", wall.um(), "od_um", od.um()));
            }
        }
    }

    /** A joint with no diameter, no cap and no seam length, which is how plate is described. */
    public Joint(JointForm form, Length wall, Material material) {
        this(form, wall, material, Optional.empty(), Length.ZERO, Optional.empty());
    }

    /** A builder holding the three things every joint has; the diameter and seam start absent, the cap at zero. */
    public static Builder builder(JointForm form, Length wall, Material material) {
        return new Builder(form, wall, material);
    }

    /**
     * The outside diameter, refusing to be asked of plate.
     *
     * @throws ValidationError for plate
     */
    public Length od() {
        return outsideDiameter.orElseThrow(
                () -> new ValidationError("plate has no outside diameter", Context.of("form", form.value())));
    }

    /** The bore: the outside diameter less two walls. */
    public Length insideDiameter() {
        return od().minus(wall.times(2));
    }

    /** The circumference at the outside surface, to the nearest micrometre. */
    public Length outsideCircumference() {
        return circumference(od());
    }

    /** The circumference at the bore, to the nearest micrometre. */
    public Length insideCircumference() {
        return circumference(insideDiameter());
    }

    /** The circumference at mid wall, to the nearest micrometre. */
    public Length meanCircumference() {
        return circumference(od().minus(wall));
    }

    /** The ratio that decides whether a source will fit inside. */
    public BigDecimal diameterToWall() {
        return od().ratio(wall);
    }

    /** Whether the outside diameter is at or under {@link #SMALL_BORE_LIMIT}; plate never is. */
    public boolean isSmallBore() {
        return outsideDiameter.map(od -> od.compareTo(SMALL_BORE_LIMIT) <= 0).orElse(false);
    }

    /** Wall plus the cap, which is what the beam actually goes through. */
    public Length totalThickness() {
        return wall.plus(reinforcement);
    }

    /** Whether a projector head physically fits down the bore with that clearance all round. */
    public boolean admitsInternalSource(Length sourceDiameter, Length clearance) {
        if (outsideDiameter.isEmpty()) {
            return false;
        }
        return insideDiameter().um() >= sourceDiameter.plus(clearance.times(2)).um();
    }

    /** The joint as the technique sheet describes it: {@code carbon steel girth weld, 168.30 mm od, 7.11 mm wall}. */
    public String describe() {
        if (outsideDiameter.isEmpty()) {
            return material + " " + form + ", " + wall.formatMm() + " wall";
        }
        return material + " " + form + ", " + od().formatMm() + " od, " + wall.formatMm() + " wall";
    }

    private static Length circumference(Length diameter) {
        if (diameter.um() <= 0) {
            throw new ValidationError("a circumference needs a positive diameter", Context.of("um", diameter.um()));
        }
        return new Length(Floats.round(diameter.um() * Math.PI));
    }

    /** Assembles a {@link Joint}; the diameter and the seam length start absent and the cap at zero. */
    public static final class Builder {

        private final JointForm form;
        private final Length wall;
        private final Material material;
        private Optional<Length> outsideDiameter = Optional.empty();
        private Length reinforcement = Length.ZERO;
        private Optional<Length> seamLength = Optional.empty();

        private Builder(JointForm form, Length wall, Material material) {
            this.form = form;
            this.wall = wall;
            this.material = material;
        }

        /** The outside diameter. */
        public Builder outsideDiameter(Length value) {
            return outsideDiameter(Optional.of(value));
        }

        /** The outside diameter, or none for plate. */
        public Builder outsideDiameter(Optional<Length> value) {
            outsideDiameter = value;
            return this;
        }

        /** The cap height the welder left. */
        public Builder reinforcement(Length value) {
            reinforcement = value;
            return this;
        }

        /** The length of a longitudinal seam. */
        public Builder seamLength(Length value) {
            return seamLength(Optional.of(value));
        }

        /** The length of a longitudinal seam, or none recorded. */
        public Builder seamLength(Optional<Length> value) {
            seamLength = value;
            return this;
        }

        /**
         * The joint as assembled.
         *
         * @throws ValidationError where the parts do not describe a joint
         */
        public Joint build() {
            return new Joint(form, wall, material, outsideDiameter, reinforcement, seamLength);
        }
    }
}
