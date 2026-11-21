package io.github.rkaushikdev.swagebay.exposure;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every factor applied to one chart reading, in the order applied.
 *
 * <p>A shooter who writes down one number has to be believed. A shooter who writes down the chart
 * reading and every factor applied to it can be checked, and the check is where the mistake turns
 * up: nine times out of ten it is a distance factor applied upside down. So the chain keeps each
 * correction with its name and reason, it is never edited once built, and nothing collapses to a
 * single factor until the caller asks for it.
 *
 * @param corrections the corrections, in the order applied
 */
public record CorrectionChain(List<Correction> corrections) {

    /** A chain; the corrections are copied. */
    public CorrectionChain {
        corrections = List.copyOf(corrections);
    }

    /** A chain with nothing in it, which leaves a reading alone. */
    public CorrectionChain() {
        this(List.of());
    }

    /** A new chain with one more correction on the end; this one is unchanged. */
    public CorrectionChain withCorrection(Correction correction) {
        List<Correction> longer = new ArrayList<>(corrections);
        longer.add(correction);
        return new CorrectionChain(longer);
    }

    /** Every factor multiplied together, in the order applied. */
    public BigDecimal combined() {
        BigDecimal total = BigDecimal.ONE;
        for (Correction correction : corrections) {
            total = Decimals.multiply(total, correction.factor());
        }
        return total;
    }

    /**
     * A chart reading with every correction applied.
     *
     * @throws ValidationError for a reading that is not positive
     */
    public BigDecimal appliedTo(BigDecimal curieMinutes) {
        if (curieMinutes.signum() <= 0) {
            throw new ValidationError("a chart reading has to be positive");
        }
        return Decimals.multiply(curieMinutes, combined());
    }

    /** The names of the corrections, in the order applied. */
    public List<String> names() {
        return corrections.stream().map(Correction::name).toList();
    }

    /** The chain as the technique sheet prints it: {@code distance x4, film x0.5}. */
    public String describe() {
        if (corrections.isEmpty()) {
            return "no corrections";
        }
        return corrections.stream().map(Correction::toString).collect(Collectors.joining(", "));
    }
}
