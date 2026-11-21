package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One instrument issued to one person.
 *
 * @param kind what it is
 * @param serial its serial number, never blank
 * @param calibrationDue the first day it is out of calibration, which every kind read on the day has
 */
public record Dosimeter(DosimeterKind kind, String serial, Optional<LocalDate> calibrationDue) {

    /**
     * A dosimeter.
     *
     * @throws ValidationError without a serial, or for a kind read on the day without a calibration date
     */
    public Dosimeter {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(calibrationDue, "calibrationDue");
        if (Text.strip(serial).isEmpty()) {
            throw new ValidationError("a dosimeter has a serial number");
        }
        if (kind != DosimeterKind.BADGE && calibrationDue.isEmpty()) {
            throw new ValidationError(
                    "an instrument that is read on the day needs a calibration date", Context.of("kind", kind.value()));
        }
    }

    /**
     * A dosimeter with no calibration date, which only a badge may be.
     *
     * @throws ValidationError without a serial, or for any kind but a badge
     */
    public Dosimeter(DosimeterKind kind, String serial) {
        this(kind, serial, Optional.empty());
    }

    /**
     * A dosimeter calibrated until that day.
     *
     * @throws ValidationError without a serial
     */
    public Dosimeter(DosimeterKind kind, String serial, LocalDate calibrationDue) {
        this(kind, serial, Optional.of(calibrationDue));
    }

    /** Whether it is inside its calibration on that day; one with no date always is. */
    public boolean isCurrentOn(LocalDate day) {
        return calibrationDue.map(day::isBefore).orElse(true);
    }
}
