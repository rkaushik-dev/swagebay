package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What recording a wipe did.
 *
 * @param serial the source wiped
 * @param passed whether the wipe came in under the limit
 * @param expiresOn the day a passing wipe stops being current
 * @param sourceState the state the source is in afterwards, as stored
 */
public record WipeResult(String serial, boolean passed, LocalDate expiresOn, String sourceState) {

    /** A result. */
    public WipeResult {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(expiresOn, "expiresOn");
        Objects.requireNonNull(sourceState, "sourceState");
    }

    /**
     * The note to the crew: {@code IR-5512 passed, current until 2025-04-13}, or {@code IR-5512
     * failed, source is now awaiting disposal}.
     */
    public String describe() {
        if (passed) {
            return serial + " passed, current until " + Times.iso(expiresOn);
        }
        return serial + " failed, source is now " + sourceState;
    }
}
