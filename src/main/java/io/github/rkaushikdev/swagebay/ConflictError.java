package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** A write would break an invariant that spans more than one record. */
public class ConflictError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.conflict";

    /** An error with a message and no context. */
    public ConflictError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public ConflictError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
