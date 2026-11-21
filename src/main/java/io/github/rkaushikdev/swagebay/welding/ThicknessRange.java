package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * The wall thicknesses a test coupon qualifies, both ends included.
 *
 * @param low the thinnest wall qualified
 * @param high the thickest wall qualified
 */
public record ThicknessRange(Length low, Length high) {

    /** Whether a wall of that thickness is inside the range. */
    public boolean contains(Length wall) {
        return low.compareTo(wall) <= 0 && wall.compareTo(high) <= 0;
    }
}
