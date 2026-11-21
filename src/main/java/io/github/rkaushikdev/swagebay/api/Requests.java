package io.github.rkaushikdev.swagebay.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;

/**
 * The checks a route runs on its body, run on a request built in code: what a test of the shapes
 * needs, and anything that builds a request rather than reading one.
 */
public final class Requests {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Requests() {}

    /**
     * The request, once every constraint on it and on what it holds has passed.
     *
     * @throws RequestRejected listing every problem, located from the request itself: {@code
     *     ["joint", "wall", "text"]}
     */
    public static <T> T validated(T request) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(request);
        if (!violations.isEmpty()) {
            throw new RequestRejected(Problems.of(violations, List.of()));
        }
        return request;
    }
}
