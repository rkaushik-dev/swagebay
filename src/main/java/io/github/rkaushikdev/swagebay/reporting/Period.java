package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * One month of work and its repair rate.
 *
 * @param year the year
 * @param month the month, one to twelve
 * @param rate the repair rate over the month
 */
public record Period(int year, int month, Rate rate) {

    /**
     * A period.
     *
     * @throws ValidationError for a month outside the calendar
     */
    public Period {
        Objects.requireNonNull(rate, "rate");
        if (month < 1 || month > 12) {
            throw new ValidationError("a month is one to twelve", Context.of("month", month));
        }
    }

    /** The first day of the month. */
    public LocalDate startsOn() {
        return LocalDate.of(year, month, 1);
    }

    /** {@code 2024-03}. */
    public String label() {
        return String.format(Locale.ROOT, "%04d-%02d", year, month);
    }
}
