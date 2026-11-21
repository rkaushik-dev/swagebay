package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** The shot as described cannot be made. */
public class TechniqueError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.technique";

    /** An error with a message and no context. */
    public TechniqueError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public TechniqueError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
