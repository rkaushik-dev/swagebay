package io.github.rkaushikdev.swagebay.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Whether a source can go out that morning, and why not.
 *
 * @param serial the source asked about
 * @param on the day asked about
 * @param fit whether nothing stands against it
 * @param reasons everything that does, in the order found
 */
public record Fitness(String serial, LocalDate on, boolean fit, List<String> reasons) {

    /** A fitness report; the reasons are copied. */
    public Fitness {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(on, "on");
        reasons = List.copyOf(reasons);
    }

    /** {@code fit}, or every reason joined with semicolons. */
    public String describe() {
        return fit ? "fit" : String.join("; ", reasons);
    }
}
