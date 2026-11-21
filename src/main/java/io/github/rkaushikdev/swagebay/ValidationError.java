package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** A value handed in by a caller is outside what the domain allows. */
public class ValidationError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.validation";

    /** An error with a message and no context. */
    public ValidationError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public ValidationError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
