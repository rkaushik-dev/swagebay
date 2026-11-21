package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** A rule table, a chart or a procedure is missing or contradicts itself. */
public class ConfigurationError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.configuration";

    /** An error with a message and no context. */
    public ConfigurationError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public ConfigurationError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
