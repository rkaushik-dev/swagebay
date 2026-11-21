package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** A radiation safety limit would be exceeded, or a control is missing. */
public class SafetyError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.safety";

    /** An error with a message and no context. */
    public SafetyError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public SafetyError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
