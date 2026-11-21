package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Text;

/**
 * One lead location marker and where it sits along the weld.
 *
 * <p>The marker goes on the part, not on the film holder. A marker taped to the cassette proves
 * where the cassette was, which is not the question: if the cassette slipped, the marker slipped with
 * it and the radiograph lies about which piece of weld it shows.
 *
 * @param label the number on the lead
 * @param at how far along the weld it sits
 */
public record Marker(String label, Length at) {

    /**
     * A marker.
     *
     * @throws ValidationError for a blank number or a position before the start of the weld
     */
    public Marker {
        if (Text.isBlank(label)) {
            throw new ValidationError("a marker needs a number");
        }
        if (at.um() < 0) {
            throw new ValidationError("a marker sits somewhere on the weld", Context.of("label", label));
        }
    }

    /** {@code 3 at 650 mm}. */
    @Override
    public String toString() {
        return label + " at " + at.formatMm(0);
    }
}
