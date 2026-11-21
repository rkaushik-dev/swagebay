package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import java.util.Objects;

/**
 * One line of the monthly dose sheet.
 *
 * @param badge whose dose it is
 * @param taken what the ledger holds for them that month
 */
public record BadgeDose(String badge, Dose taken) {

    /** A line. */
    public BadgeDose {
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(taken, "taken");
    }
}
