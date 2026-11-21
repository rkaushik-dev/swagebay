package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Objects;

/**
 * A pad of parent material under an indicator, making up the cap the indicator does not sit on.
 *
 * @param thickness how thick it is, positive
 * @param materialKey the key of the material it is cut from, the same as the part's
 */
public record Shim(Length thickness, String materialKey) {

    /**
     * A shim.
     *
     * @throws ValidationError for a thickness that is not positive
     */
    public Shim {
        Objects.requireNonNull(thickness, "thickness");
        Objects.requireNonNull(materialKey, "materialKey");
        if (thickness.um() <= 0) {
            throw new ValidationError("a shim with no thickness is not a shim");
        }
    }

    /** The shim as the sheet writes it: {@code 2.50 mm carbon_steel shim}. */
    @Override
    public String toString() {
        return thickness.formatMm() + " " + materialKey + " shim";
    }
}
