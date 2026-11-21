package io.github.rkaushikdev.swagebay.scheduling;

import io.github.rkaushikdev.swagebay.util.Repr;

/**
 * Why a window is open, which matters because the reasons fail differently. A night window closes
 * at first light and there is nothing to negotiate. A shutdown window is open all day and can be
 * extended by asking. A permit window is open only while a written permit is live, and the permit
 * can be pulled with no notice at all.
 */
public enum WindowKind {
    NIGHT("night"),
    WEEKEND("weekend"),
    SHUTDOWN("shutdown"),
    PERMIT("permit");

    private final String value;

    WindowKind(String value) {
        this.value = value;
    }

    /**
     * The kind with that stored word.
     *
     * @throws IllegalArgumentException for a word that names no kind of window
     */
    public static WindowKind fromValue(String value) {
        for (WindowKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no window kind is stored as " + Repr.of(value));
    }

    /** The stored word, {@code shutdown}. */
    public String value() {
        return value;
    }

    /** Whether the client can be asked to keep the window open longer. */
    public boolean isExtendable() {
        return this == SHUTDOWN || this == WEEKEND;
    }

    /** The stored word. */
    @Override
    public String toString() {
        return value;
    }
}
