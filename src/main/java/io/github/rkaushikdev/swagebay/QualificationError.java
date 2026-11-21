package io.github.rkaushikdev.swagebay;

import java.util.Map;

/** An image that exists is not admissible. */
public class QualificationError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.qualification";

    /** An error with a message and no context. */
    public QualificationError(String message) {
        super(message);
    }

    /** An error with a message and the facts that explain it. */
    public QualificationError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
