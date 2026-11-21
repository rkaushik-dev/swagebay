package io.github.rkaushikdev.swagebay.services;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One line of a source history sheet.
 *
 * @param at when it happened; a day with no time on it reads as its midnight
 * @param what what happened: {@code assay}, {@code leak test}, {@code sent out}, {@code came back}
 *     or {@code sighted}
 * @param detail the rest of the line
 */
public record HistoryEntry(LocalDateTime at, String what, String detail) {

    /** An entry. */
    public HistoryEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(detail, "detail");
    }
}
