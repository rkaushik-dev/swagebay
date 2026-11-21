package io.github.rkaushikdev.swagebay.reporting;

import java.util.Objects;

/**
 * One step of a traceability chain, with whether it holds.
 *
 * @param name what the step is: {@code job}, {@code weld}, {@code read by}, {@code procedure}
 * @param value what the record says it is
 * @param holds whether it can be established and was valid on the night
 * @param note why it does not hold, or what was not checked; empty when there is nothing to say
 */
public record Link(String name, String value, boolean holds, String note) {

    /** A link. */
    public Link {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(note, "note");
    }

    /** A link with nothing to note about it. */
    public Link(String name, String value, boolean holds) {
        this(name, value, holds, "");
    }

    /** {@code read by: nobody [broken] (not yet read)}. */
    public String describe() {
        String state = holds ? "ok" : "broken";
        String detail = note.isEmpty() ? "" : " (" + note + ")";
        return name + ": " + value + " [" + state + "]" + detail;
    }
}
