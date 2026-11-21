package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;

/**
 * Where a film physically is. A film that cannot be found has not been retained, so every part of
 * the location is required.
 *
 * @param site the site the cabinet is at
 * @param cabinet the cabinet
 * @param box the box in the cabinet
 */
public record Location(String site, String cabinet, String box) {

    /**
     * A location.
     *
     * @throws ValidationError naming the first part that is blank
     */
    public Location {
        required("site", site);
        required("cabinet", cabinet);
        required("box", box);
    }

    /** {@code Come By Chance cabinet A box 14}, the way somebody would walk to it. */
    public String describe() {
        return site + " cabinet " + cabinet + " box " + box;
    }

    private static void required(String name, String value) {
        if (Text.isBlank(value)) {
            throw new ValidationError("a location needs this filled in", Context.of("field", name));
        }
    }
}
