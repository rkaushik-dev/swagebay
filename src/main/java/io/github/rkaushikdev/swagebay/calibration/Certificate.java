package io.github.rkaushikdev.swagebay.calibration;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One calibration, as the laboratory issued it.
 *
 * <p>The due date is stored rather than derived, because a laboratory sometimes issues a
 * certificate valid to a date of its own, and then the certificate wins.
 *
 * @param calibratedOn when the calibration was done
 * @param dueOn the first day it no longer holds
 * @param laboratory who did it, never blank
 * @param reference the certificate number
 */
public record Certificate(LocalDate calibratedOn, LocalDate dueOn, String laboratory, String reference) {

    /**
     * A certificate.
     *
     * @throws ValidationError for a due date on or before the calibration, or without a laboratory
     */
    public Certificate {
        Objects.requireNonNull(reference, "reference");
        if (!dueOn.isAfter(calibratedOn)) {
            throw new ValidationError(
                    "a certificate expires after it was issued",
                    Context.of("calibrated_on", Times.iso(calibratedOn), "due_on", Times.iso(dueOn)));
        }
        if (Text.strip(laboratory).isEmpty()) {
            throw new ValidationError("a certificate names the laboratory");
        }
    }

    /** Whether the certificate holds on that day: from the calibration up to the day before it is due. */
    public boolean isCurrentOn(LocalDate day) {
        return !day.isBefore(calibratedOn) && day.isBefore(dueOn);
    }
}
