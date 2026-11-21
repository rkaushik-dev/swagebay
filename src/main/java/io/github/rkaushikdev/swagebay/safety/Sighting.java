package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;

/**
 * Somebody laid eyes on a source on a day and signed for it.
 *
 * @param sourceSerial the source that was seen
 * @param seenOn the day it was seen
 * @param seenBy the badge of whoever signed
 * @param location where it was
 */
public record Sighting(String sourceSerial, LocalDate seenOn, String seenBy, String location) {

    /**
     * A sighting.
     *
     * @throws ValidationError when nobody signed it
     */
    public Sighting {
        if (Text.strip(seenBy).isEmpty()) {
            throw new ValidationError("an inventory sighting is signed");
        }
    }
}
