package io.github.rkaushikdev.swagebay.audit;

import io.github.rkaushikdev.swagebay.util.Repr;

/** What happened to a record. */
public enum Action {
    CREATED("created"),
    CHANGED("changed"),
    DELETED("deleted"),
    SIGNED("signed");

    private final String value;

    Action(String value) {
        this.value = value;
    }

    /**
     * The action with that stored word.
     *
     * @throws IllegalArgumentException for a word that names no action
     */
    public static Action fromValue(String value) {
        for (Action action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("no audit action is stored as " + Repr.of(value));
    }

    /** The stored word, {@code changed}. */
    public String value() {
        return value;
    }

    /** The stored word. */
    @Override
    public String toString() {
        return value;
    }
}
