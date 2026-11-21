package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * Welder qualification, which is the other credential a radiograph depends on.
 *
 * <p>A radiograph of a weld made by an unqualified welder is a radiograph of a weld that has to come
 * out whatever it looks like. A welder qualifies by making one test weld in one position on one
 * thickness, and that single test buys a range of positions, a range of thicknesses and a group of
 * materials. The thickness range is not symmetric: a thin coupon qualifies thin work only, while a
 * thick one qualifies everything from a floor up to twice the coupon.
 */
public final class Welding {

    /** Six months without using a process and the qualification lapses. */
    public static final int CONTINUITY_DAYS = 180;

    /** At or under this the coupon qualifies only up to twice its own thickness. */
    public static final Length THIN_COUPON = Length.fromMm(10);

    private static final Length THICK_FLOOR = Length.fromMm(5);

    private Welding() {}

    /**
     * What a coupon of that thickness qualifies: from nothing up to twice itself for a thin coupon,
     * from five millimetres up to twice itself for a thick one.
     *
     * @throws ValidationError for a coupon with no thickness
     */
    public static ThicknessRange thicknessRange(Length coupon) {
        if (coupon.um() <= 0) {
            throw new ValidationError("a coupon has a thickness");
        }
        if (coupon.compareTo(THIN_COUPON) <= 0) {
            return new ThicknessRange(Length.ZERO, coupon.times(2));
        }
        return new ThicknessRange(THICK_FLOOR, coupon.times(2));
    }
}
