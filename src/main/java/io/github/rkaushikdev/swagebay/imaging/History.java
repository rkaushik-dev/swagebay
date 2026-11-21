package io.github.rkaushikdev.swagebay.imaging;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Everything done to one digital image, in the order it was done.
 *
 * <p>The order changes the result, and a list without an order is not a record of what happened.
 * A history is immutable: {@link #then} gives a new one, so a record cannot be edited.
 *
 * @param operations the operations, first applied first
 */
public record History(List<Applied> operations) {

    /** A history; the operations are copied. */
    public History {
        operations = List.copyOf(operations);
    }

    /** An image nobody has touched. */
    public History() {
        this(List.of());
    }

    /** This history with one more operation at the end. */
    public History then(Applied applied) {
        List<Applied> longer = new ArrayList<>(operations);
        longer.add(applied);
        return new History(longer);
    }

    /** Whether every operation left the stored data alone. */
    public boolean isReversible() {
        return operations.stream().allMatch(item -> item.operation().isReversible());
    }

    /** The operations that threw information away, in order. */
    public List<Applied> irreversible() {
        return operations.stream()
                .filter(item -> !item.operation().isReversible())
                .toList();
    }

    /** {@code unprocessed}, or each operation in order joined with {@code then}. */
    public String describe() {
        if (operations.isEmpty()) {
            return "unprocessed";
        }
        return operations.stream().map(Applied::describe).collect(Collectors.joining(" then "));
    }
}
