package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Objects;

/**
 * One thing wrong, on one item.
 *
 * @param item the item it is on
 * @param account what is wrong with it, never blank
 */
public record Defect(InspectionItem item, String account) {

    /**
     * A defect.
     *
     * @throws ValidationError when it does not say what is wrong
     */
    public Defect {
        Objects.requireNonNull(item, "item");
        if (Text.strip(account).isEmpty()) {
            throw new ValidationError("a defect says what is wrong with it", Context.of("item", item.toString()));
        }
    }

    /** Whether it stops the device, which the item decides. */
    public boolean stopsTheDevice() {
        return item.stopsTheDevice();
    }

    /** The sheet line: {@code wheels: one castor seized}. */
    public String describe() {
        return item + ": " + account;
    }
}
