package io.github.rkaushikdev.swagebay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The named facts an error carries, built in the order they are given.
 *
 * <p>{@code new ValidationError("not a length", Context.of("text", text))}. Values may be absent,
 * which an error reports as {@code None}.
 */
public final class Context {

    private Context() {}

    /**
     * A context from alternating names and values.
     *
     * @throws IllegalArgumentException when a name is missing or is not text
     */
    public static Map<String, Object> of(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("a context is pairs of a name and a value");
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (!(namesAndValues[i] instanceof String name)) {
                throw new IllegalArgumentException("a context name is text: " + namesAndValues[i]);
            }
            facts.put(name, namesAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(facts);
    }
}
