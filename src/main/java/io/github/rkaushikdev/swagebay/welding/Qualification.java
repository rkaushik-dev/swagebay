package io.github.rkaushikdev.swagebay.welding;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * One welder, one process, one test weld and the ranges it bought.
 *
 * @param welder the welder's stamp, never blank
 * @param process the process qualified
 * @param position the position the test weld was made in
 * @param coupon the thickness of the test coupon, above zero
 * @param qualifiedOn the day of the test
 * @param materialGroup the material group qualified, {@code P1}
 */
public record Qualification(
        String welder,
        WeldingProcess process,
        Position position,
        Length coupon,
        LocalDate qualifiedOn,
        String materialGroup) {

    /**
     * A qualification.
     *
     * @throws ValidationError without a welder, or for a coupon with no thickness
     */
    public Qualification {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(qualifiedOn, "qualifiedOn");
        Objects.requireNonNull(materialGroup, "materialGroup");
        if (Text.strip(welder).isEmpty()) {
            throw new ValidationError("a qualification names a welder");
        }
        if (coupon.um() <= 0) {
            throw new ValidationError("a coupon has a thickness", Context.of("welder", welder));
        }
    }

    /** The positions the test weld qualifies. */
    public Set<Position> positions() {
        return position.qualifies();
    }

    /** The wall thicknesses the coupon qualifies. */
    public ThicknessRange thicknesses() {
        return Welding.thicknessRange(coupon);
    }

    /** Whether the qualification covers a weld in that position, on that wall, in that material group. */
    public boolean covers(Position weldPosition, Length wall, String group) {
        return positions().contains(weldPosition) && thicknesses().contains(wall) && group.equals(materialGroup);
    }

    /** The record line: {@code w-19 shielded metal arc 6G, 5.0 mm to 24.0 mm, group P1}. */
    public String describe() {
        ThicknessRange range = thicknesses();
        return welder + " " + process + " " + position + ", " + range.low().formatMm(1) + " to "
                + range.high().formatMm(1) + ", group " + materialGroup;
    }
}
