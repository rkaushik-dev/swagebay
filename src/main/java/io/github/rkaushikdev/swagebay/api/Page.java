package io.github.rkaushikdev.swagebay.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page, and where the next one starts.
 *
 * @param items the rows on this page, in the listing's order
 * @param nextCursor the key of the last row on this page when there is a next page; empty on the
 *     last one
 * @param size the page size the listing was cut to
 */
public record Page<T>(List<T> items, Optional<String> nextCursor, int size) {

    public Page {
        items = List.copyOf(items);
        Objects.requireNonNull(nextCursor, "nextCursor");
    }

    /** Whether nothing follows this page. */
    public boolean isLast() {
        return nextCursor.isEmpty();
    }

    /** How many rows are on this page. */
    public int count() {
        return items.size();
    }
}
