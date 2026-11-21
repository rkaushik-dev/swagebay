package io.github.rkaushikdev.swagebay;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The root of everything this package throws deliberately.
 *
 * <p>Two branches are worth keeping apart. A {@link TechniqueError} means the shot as described
 * cannot be made: the geometry is impossible, the source is spent, the chart does not reach that
 * thickness. A {@link QualificationError} means the shot was made and the image it produced is
 * not admissible. The first stops work before the source comes out of the pig. The second is a
 * finding against an image that already exists, and somebody has to be told which one and why.
 *
 * <p>Every error carries a stable {@link #code()} and a context of named facts. The codes end up
 * in report packages that are read years later by people who no longer have the software, so they
 * do not get renamed once they ship.
 */
public class SwagebayError extends RuntimeException {

    /** The code of this class of error. */
    public static final String CODE = "qi.error";

    private final String message;
    private final Map<String, Object> context;

    /** An error with a message and no context. */
    public SwagebayError(String message) {
        this(message, Map.of());
    }

    /** An error with a message and the facts that explain it, kept in the order given. */
    public SwagebayError(String message, Map<String, ?> context) {
        super(render(message, context));
        this.message = message;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /** The stable code of this class of error. */
    public String code() {
        return CODE;
    }

    /** The sentence on its own, without the context. */
    public String message() {
        return message;
    }

    /** The named facts behind the error. */
    public Map<String, Object> context() {
        return context;
    }

    /** The shape that goes into a report or a response body: code, message and a copy of the context. */
    public Map<String, Object> asMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code());
        body.put("message", message);
        body.put("context", new LinkedHashMap<>(context));
        return body;
    }

    private static String render(String message, Map<String, ?> context) {
        if (context.isEmpty()) {
            return message;
        }
        String facts = new TreeMap<>(context)
                .entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + Repr.of(entry.getValue()))
                        .collect(Collectors.joining(", "));
        return message + " (" + facts + ")";
    }
}
