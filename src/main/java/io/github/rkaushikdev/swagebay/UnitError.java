package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** Two quantities were combined that do not share a dimension. */
public class UnitError extends ValidationError {

    /** The code of this class of error. */
    public static final String CODE = "qi.unit";

    /** An error with a message and no context. */
    public UnitError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public UnitError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
